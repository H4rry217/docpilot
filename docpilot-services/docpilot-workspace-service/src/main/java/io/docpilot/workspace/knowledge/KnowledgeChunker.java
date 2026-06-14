package io.docpilot.workspace.knowledge;

import io.docpilot.block.model.BlockDocument;
import io.docpilot.block.model.BlockNode;
import io.docpilot.block.model.BlockType;
import io.docpilot.block.model.InlineNode;
import io.docpilot.block.processing.MarkdownBlockRenderer;
import io.docpilot.block.typed.BlockAttrs;
import io.docpilot.workspace.knowledge.model.KnowledgeChunkDraft;
import io.docpilot.workspace.knowledge.model.KnowledgeChunkingInput;
import io.docpilot.workspace.knowledge.model.KnowledgeChunkingResult;
import io.docpilot.workspace.knowledge.model.KnowledgeSectionDraft;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts DocPilot block snapshots into semantic search chunks.
 *
 * <p>The chunker only reads a revision snapshot. It does not know workspace ids,
 * document ids, embeddings, or ES details; those fields are attached later by the
 * indexing command handler.</p>
 */
public class KnowledgeChunker {

    /**
     * Default maximum characters sent to one section summary request.
     */
    private static final int DEFAULT_SUMMARY_PART_MAX_CHARS = 1000;

    /**
     * Minimum direct child sections required before generating a parent summary.
     */
    private static final int SUMMARY_CHILD_SECTION_THRESHOLD = 2;

    /**
     * Minimum direct content block count required before generating a summary.
     */
    private static final int SUMMARY_DIRECT_BLOCK_THRESHOLD = 5;

    /**
     * Minimum total text length for summary generation without complex blocks.
     */
    private static final int SUMMARY_TOTAL_TEXT_THRESHOLD = 1200;

    /**
     * Minimum total text length for summary generation when complex blocks are present.
     */
    private static final int SUMMARY_COMPLEX_TEXT_THRESHOLD = 600;

    /**
     * Markdown renderer used to turn block subtrees into indexable text.
     */
    private final MarkdownBlockRenderer markdownBlockRenderer;

    /**
     * Maximum rendered section characters per summary draft. Values <= 0 disable active splitting.
     */
    private final int summaryPartMaxChars;

    public KnowledgeChunker() {
        this(new MarkdownBlockRenderer(), DEFAULT_SUMMARY_PART_MAX_CHARS);
    }

    public KnowledgeChunker(int summaryPartMaxChars) {
        this(new MarkdownBlockRenderer(), summaryPartMaxChars);
    }

    public KnowledgeChunker(MarkdownBlockRenderer markdownBlockRenderer) {
        this(markdownBlockRenderer, DEFAULT_SUMMARY_PART_MAX_CHARS);
    }

    public KnowledgeChunker(MarkdownBlockRenderer markdownBlockRenderer, int summaryPartMaxChars) {
        this.markdownBlockRenderer = markdownBlockRenderer == null ? new MarkdownBlockRenderer() : markdownBlockRenderer;
        this.summaryPartMaxChars = summaryPartMaxChars;
    }

    /**
     * Split one BlockDocument snapshot into original block chunks and complex heading sections.
     *
     * <p>Execution rules:</p>
     * <ul>
     *     <li>Iterate top-level blocks in reading order.</li>
     *     <li>Heading blocks update a heading stack, create a short BLOCK chunk, and open a section tree node.</li>
     *     <li>Indexable non-heading blocks create one BLOCK chunk from rendered markdown.</li>
     *     <li>Container blocks such as lists, tables, quotes, and callouts are rendered as one chunk.</li>
     *     <li>Only complex section tree nodes produce SECTION_SUMMARY drafts.</li>
     * </ul>
     */
    public KnowledgeChunkingResult chunk(KnowledgeChunkingInput input) {
        BlockDocument snapshot = input == null ? null : input.getSnapshot();
        if (isEmptySnapshot(snapshot)) {
            return new KnowledgeChunkingResult(List.of(), List.of());
        }

        List<KnowledgeChunkDraft> chunks = new ArrayList<>();
        List<SectionNode> rootSections = new ArrayList<>();
        List<SectionNode> sectionStack = new ArrayList<>();

        for (BlockNode block : snapshot.getBlocks()) {
            if (block == null || block.getType() == null) {
                continue;
            }

            if (block.getType() == BlockType.HEADING) {
                handleHeadingBlock(block, chunks, rootSections, sectionStack);
                continue;
            }

            handleContentBlock(block, chunks, sectionStack);
        }

        List<KnowledgeSectionDraft> sections = new ArrayList<>();
        for (SectionNode section : rootSections) {
            collectSummarySections(section, sections);
        }
        return new KnowledgeChunkingResult(List.copyOf(chunks), List.copyOf(sections));
    }

    /**
     * Return true when the snapshot has no blocks that can be walked.
     */
    private boolean isEmptySnapshot(BlockDocument snapshot) {
        return snapshot == null || snapshot.getBlocks() == null || snapshot.getBlocks().isEmpty();
    }

    /**
     * Update the section stack, index the heading itself, and attach a new section tree node.
     */
    private void handleHeadingBlock(BlockNode block,
                                    List<KnowledgeChunkDraft> chunks,
                                    List<SectionNode> rootSections,
                                    List<SectionNode> sectionStack) {
        int level = headingLevel(block);
        while (!sectionStack.isEmpty() && sectionStack.getLast().level >= level) {
            sectionStack.removeLast();
        }

        List<String> headingPath = new ArrayList<>();
        if (!sectionStack.isEmpty()) {
            headingPath.addAll(sectionStack.getLast().headingPath);
        }
        headingPath.add(plainText(block.getInlines()).strip());

        String headingContent = renderAsMarkdown(block);
        addBlockChunk(chunks, block, headingPath, headingContent);

        SectionNode section = new SectionNode(block.getId(), level, headingPath);
        if (sectionStack.isEmpty()) {
            rootSections.add(section);
        } else {
            sectionStack.getLast().children.add(section);
        }
        sectionStack.add(section);
    }

    /**
     * Render and index a non-heading block when its type is searchable and its text is non-empty.
     */
    private void handleContentBlock(BlockNode block,
                                    List<KnowledgeChunkDraft> chunks,
                                    List<SectionNode> sectionStack) {
        if (!shouldIndex(block.getType())) {
            return;
        }

        String content = renderAsMarkdown(block);
        if (content.isBlank()) {
            return;
        }

        List<String> headingPath = sectionStack.isEmpty() ? List.of() : sectionStack.getLast().headingPath;
        addBlockChunk(chunks, block, headingPath, content);
        if (!sectionStack.isEmpty()) {
            sectionStack.getLast().appendContent(content, isComplexBlock(block.getType()));
        }
    }

    /**
     * Add one BLOCK chunk for a source block.
     *
     * <p>v1 keeps chunkIndex at 0 because each indexed block becomes one chunk. The field
     * remains in the draft so future long-block splitting can keep stable ids.</p>
     */
    private void addBlockChunk(List<KnowledgeChunkDraft> chunks,
                               BlockNode block,
                               List<String> headingPath,
                               String content) {
        chunks.add(KnowledgeChunkDraft.block(
                block.getId(),
                block.getType().name(),
                0,
                headingPath,
                content.strip()
        ));
    }

    /**
     * Render a single block subtree into markdown text used for search and embedding.
     */
    private String renderAsMarkdown(BlockNode block) {
        return markdownBlockRenderer.render(BlockDocument.of(List.of(block))).strip();
    }

    /**
     * Decide whether a block type should produce a BLOCK chunk.
     *
     * <p>Child-only structural nodes are skipped because their parent container is indexed
     * as one chunk. Generated navigation and reference metadata are skipped because they
     * add little value for inline completion.</p>
     */
    private boolean shouldIndex(BlockType type) {
        return switch (type) {
            case PARAGRAPH, HEADING, BLOCK_QUOTE, BULLET_LIST, ORDERED_LIST, TABLE,
                 CALLOUT, FOOTNOTE_DEFINITION, DEFINITION_LIST, CODE_BLOCK, MATH_BLOCK,
                 DIAGRAM_BLOCK, HTML_BLOCK, EXTENSION_BLOCK, UNSUPPORTED_BLOCK -> true;
            case DOCUMENT, LIST_ITEM, TASK_LIST_ITEM, THEMATIC_BREAK, TABLE_ROW, TABLE_CELL,
                 FRONT_MATTER, DEFINITION_TERM, DEFINITION_ITEM, TOC, LINK_REFERENCE_DEFINITION -> false;
        };
    }

    /**
     * Return true when the block is dense enough to make its section summary-worthy at a lower text threshold.
     */
    private boolean isComplexBlock(BlockType type) {
        return switch (type) {
            case BLOCK_QUOTE, BULLET_LIST, ORDERED_LIST, TABLE, CALLOUT, FOOTNOTE_DEFINITION,
                 DEFINITION_LIST, CODE_BLOCK, MATH_BLOCK, DIAGRAM_BLOCK, HTML_BLOCK -> true;
            default -> false;
        };
    }

    /**
     * Read and clamp a heading level from block attrs.
     */
    private int headingLevel(BlockNode heading) {
        Object value = heading.getAttrs() == null ? null : heading.getAttrs().get(BlockAttrs.LEVEL.key());
        if (value instanceof Number number) {
            return Math.max(1, Math.min(6, number.intValue()));
        }
        if (value instanceof String text) {
            try {
                return Math.max(1, Math.min(6, Integer.parseInt(text)));
            } catch (NumberFormatException ignored) {
            }
        }
        return 1;
    }

    /**
     * Extract plain inline text for headingPath labels.
     */
    private String plainText(List<InlineNode> inlines) {
        if (inlines == null || inlines.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (InlineNode inline : inlines) {
            if (inline != null && inline.getText() != null) {
                text.append(inline.getText());
            }
        }
        return text.toString();
    }

    /**
     * Walk the section tree and keep only complex summary candidates.
     */
    private void collectSummarySections(SectionNode section, List<KnowledgeSectionDraft> sections) {
        if (section.shouldSummarize()) {
            sections.addAll(section.toDrafts(summaryPartMaxChars));
        }
        for (SectionNode child : section.children) {
            collectSummarySections(child, sections);
        }
    }

    /**
     * One heading-backed section node in the document outline.
     */
    private static class SectionNode {

        /**
         * Source heading block id used by the derived summary chunk.
         */
        private final String headingBlockId;

        /**
         * Heading level clamped to Markdown levels 1 through 6.
         */
        private final int level;

        /**
         * Heading labels from document root to this section.
         */
        private final List<String> headingPath;

        /**
         * Nested child sections in document order.
         */
        private final List<SectionNode> children = new ArrayList<>();

        /**
         * Rendered content blocks directly owned by this section, excluding child sections.
         */
        private final List<String> directContentBlocks = new ArrayList<>();

        /**
         * Number of indexable direct blocks in this section.
         */
        private int directBlockCount;

        /**
         * Whether this section directly contains a dense or structured block.
         */
        private boolean hasDirectComplexBlock;

        /**
         * Create a section node for one heading.
         */
        SectionNode(String headingBlockId, int level, List<String> headingPath) {
            this.headingBlockId = headingBlockId;
            this.level = level;
            this.headingPath = new ArrayList<>(headingPath);
        }

        /**
         * Append direct block content to this section.
         */
        void appendContent(String value, boolean complexBlock) {
            if (value == null || value.isBlank()) {
                return;
            }
            directContentBlocks.add(value.strip());
            directBlockCount++;
            hasDirectComplexBlock = hasDirectComplexBlock || complexBlock;
        }

        /**
         * Return true when this section is complex enough to justify a derived summary chunk.
         */
        boolean shouldSummarize() {
            return hasContent()
                    && (children.size() >= SUMMARY_CHILD_SECTION_THRESHOLD
                    || directBlockCount >= SUMMARY_DIRECT_BLOCK_THRESHOLD
                    || totalTextChars() >= SUMMARY_TOTAL_TEXT_THRESHOLD
                    || hasComplexBlock() && totalTextChars() >= SUMMARY_COMPLEX_TEXT_THRESHOLD);
        }

        /**
         * Build section drafts consumed by KnowledgeSummaryService.
         */
        List<KnowledgeSectionDraft> toDrafts(int maxChars) {
            List<String> parts = summaryParts(maxChars);
            List<KnowledgeSectionDraft> drafts = new ArrayList<>();
            for (int index = 0; index < parts.size(); index++) {
                KnowledgeSectionDraft draft = new KnowledgeSectionDraft();
                draft.setHeadingBlockId(headingBlockId);
                draft.setHeadingPath(new ArrayList<>(headingPath));
                draft.setChunkIndex(index);
                draft.setContent(parts.get(index));
                drafts.add(draft);
            }
            return drafts;
        }

        private boolean hasContent() {
            return !directContentBlocks.isEmpty() || children.stream().anyMatch(SectionNode::hasContent);
        }

        private int totalTextChars() {
            int total = directContentBlocks.stream()
                    .mapToInt(String::length)
                    .sum();
            for (SectionNode child : children) {
                total += child.totalTextChars();
            }
            return total;
        }

        private boolean hasComplexBlock() {
            return hasDirectComplexBlock || children.stream().anyMatch(SectionNode::hasComplexBlock);
        }

        private String summaryContent() {
            return String.join("\n\n", summaryContentUnits());
        }

        private List<String> summaryContentUnits() {
            List<String> units = new ArrayList<>(directContentBlocks);
            for (SectionNode child : children) {
                if (child.hasContent()) {
                    units.add(child.summaryContentWithHeading());
                }
            }
            return units;
        }

        private List<String> summaryParts(int maxChars) {
            String content = summaryContent().strip();
            if (content.isBlank()) {
                return List.of();
            }
            if (maxChars <= 0 || content.length() <= maxChars) {
                return List.of(content);
            }

            List<String> parts = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (String unit : summaryContentUnits()) {
                appendUnit(parts, current, unit, maxChars);
            }
            if (!current.isEmpty()) {
                parts.add(current.toString().strip());
            }
            return List.copyOf(parts);
        }

        private void appendUnit(List<String> parts, StringBuilder current, String unit, int maxChars) {
            if (unit == null || unit.isBlank()) {
                return;
            }
            for (String piece : splitOversizedUnit(unit.strip(), maxChars)) {
                appendPiece(parts, current, piece, maxChars);
            }
        }

        private void appendPiece(List<String> parts, StringBuilder current, String piece, int maxChars) {
            if (piece == null || piece.isBlank()) {
                return;
            }
            String value = piece.strip();
            if (current.isEmpty()) {
                current.append(value);
                return;
            }
            if (current.length() + 2 + value.length() <= maxChars) {
                current.append("\n\n").append(value);
                return;
            }
            parts.add(current.toString().strip());
            current.setLength(0);
            current.append(value);
        }

        private List<String> splitOversizedUnit(String unit, int maxChars) {
            if (unit.length() <= maxChars) {
                return List.of(unit);
            }
            List<String> parts = new ArrayList<>();
            String[] paragraphs = unit.split("\\R\\s*\\R");
            if (paragraphs.length > 1) {
                StringBuilder current = new StringBuilder();
                for (String paragraph : paragraphs) {
                    appendUnit(parts, current, paragraph, maxChars);
                }
                if (!current.isEmpty()) {
                    parts.add(current.toString().strip());
                }
                return List.copyOf(parts);
            }
            for (int start = 0; start < unit.length(); start += maxChars) {
                int end = Math.min(unit.length(), start + maxChars);
                parts.add(unit.substring(start, end).strip());
            }
            return List.copyOf(parts);
        }

        private String summaryContentWithHeading() {
            StringBuilder content = new StringBuilder();
            String heading = headingPath.isEmpty() ? "" : headingPath.getLast();
            if (!heading.isBlank()) {
                content.append("#".repeat(Math.max(1, Math.min(6, level))))
                        .append(" ")
                        .append(heading)
                        .append("\n\n");
            }
            content.append(summaryContent());
            return content.toString().strip();
        }

    }

}
