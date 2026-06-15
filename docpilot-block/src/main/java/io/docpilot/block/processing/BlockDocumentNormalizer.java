package io.docpilot.block.processing;

import io.docpilot.block.model.BlockDocument;
import io.docpilot.block.model.BlockNode;
import io.docpilot.block.model.BlockType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes legacy block snapshots before they are sent to editors.
 */
public class BlockDocumentNormalizer {

    private static final String FRONTEND_TRANSIENT_BLOCK_ID_PREFIX = "docpilot-transient-";
    private static final String LEGACY_FRONTEND_TRANSIENT_BLOCK_ID_PREFIX = "frontend-";
    private static final Pattern DETAILS_OPEN_BLOCK = Pattern.compile("(?is)^\\s*<details\\b([^>]*)>\\s*(?:<summary\\b[^>]*>(.*?)</summary>)?\\s*$");
    private static final Pattern DETAILS_CLOSE_BLOCK = Pattern.compile("(?is)^\\s*</details>\\s*$");
    private static final Pattern DETAILS_OPEN_ATTR = Pattern.compile("(?i)(^|\\s)open(\\s|=|$)");

    private final MarkdownBlockParser markdownBlockParser;
    private final BlockIdGenerator blockIdGenerator;

    public BlockDocumentNormalizer() {
        this(new MarkdownBlockParser(), new BlockIdGenerator());
    }

    public BlockDocumentNormalizer(MarkdownBlockParser markdownBlockParser) {
        this(markdownBlockParser, new BlockIdGenerator());
    }

    public BlockDocumentNormalizer(MarkdownBlockParser markdownBlockParser, BlockIdGenerator blockIdGenerator) {
        this.markdownBlockParser = markdownBlockParser == null ? new MarkdownBlockParser() : markdownBlockParser;
        this.blockIdGenerator = blockIdGenerator == null ? new BlockIdGenerator() : blockIdGenerator;
    }

    public BlockDocument normalizeForEditing(BlockDocument document) {
        BlockDocument normalized = new BlockDocument();
        if (document == null) {
            return normalized;
        }

        normalized.setSchemaVersion(document.getSchemaVersion());
        normalized.setMetadata(document.getMetadata() == null ? new HashMap<>() : new HashMap<>(document.getMetadata()));
        normalized.setBlocks(normalizeBlocks(document.getBlocks()));
        return normalized;
    }

    public BlockDocument normalizeForStorage(BlockDocument document) {
        BlockDocument normalized = new BlockDocument();
        if (document == null) {
            return normalized;
        }

        normalized.setSchemaVersion(document.getSchemaVersion());
        normalized.setMetadata(document.getMetadata() == null ? new HashMap<>() : new HashMap<>(document.getMetadata()));
        normalized.setBlocks(normalizeBlocksForStorage(document.getBlocks(), new HashSet<>()));
        return normalized;
    }

    private List<BlockNode> normalizeBlocks(List<BlockNode> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return new ArrayList<>();
        }

        List<BlockNode> normalized = new ArrayList<>();
        for (int index = 0; index < blocks.size(); index += 1) {
            BlockNode block = blocks.get(index);
            LegacyDetailsSequence detailsSequence = legacyDetailsSequence(blocks, index);
            if (detailsSequence != null) {
                normalized.add(detailsSequence.block());
                index = detailsSequence.closeIndex();
                continue;
            }

            List<BlockNode> detailsBlocks = legacyDetailsBlocks(block);
            if (detailsBlocks != null) {
                normalized.addAll(detailsBlocks);
            } else {
                normalized.add(copyBlock(block));
            }
        }
        return normalized;
    }

    private LegacyDetailsSequence legacyDetailsSequence(List<BlockNode> blocks, int startIndex) {
        DetailsOpening opening = detailsOpening(blocks.get(startIndex));
        if (opening == null) {
            return null;
        }

        int depth = 1;
        for (int index = startIndex + 1; index < blocks.size(); index += 1) {
            BlockNode candidate = blocks.get(index);
            if (detailsOpening(candidate) != null) {
                depth += 1;
                continue;
            }

            if (!isDetailsClose(candidate)) {
                continue;
            }

            depth -= 1;
            if (depth > 0) {
                continue;
            }

            BlockNode openingBlock = blocks.get(startIndex);
            BlockNode details = BlockNode.of(
                    openingBlock.getId(),
                    BlockType.CALLOUT,
                    detailsAttrs(opening),
                    List.of(),
                    normalizeBlocks(blocks.subList(startIndex + 1, index)),
                    openingBlock.getSourceRange()
            );
            return new LegacyDetailsSequence(index, details);
        }

        return null;
    }

    private DetailsOpening detailsOpening(BlockNode block) {
        String source = htmlSource(block);
        if (source == null) {
            return null;
        }

        Matcher matcher = DETAILS_OPEN_BLOCK.matcher(source);
        if (!matcher.matches()) {
            return null;
        }

        String attrs = matcher.group(1) == null ? "" : matcher.group(1);
        String title = htmlText(matcher.group(2));
        return new DetailsOpening(title.isBlank() ? "Details" : title, DETAILS_OPEN_ATTR.matcher(attrs).find());
    }

    private boolean isDetailsClose(BlockNode block) {
        String source = htmlSource(block);
        return source != null && DETAILS_CLOSE_BLOCK.matcher(source).matches();
    }

    private List<BlockNode> legacyDetailsBlocks(BlockNode block) {
        String source = htmlSource(block);
        if (source == null || !source.stripLeading().startsWith("<details")) {
            return null;
        }

        List<BlockNode> parsedBlocks = markdownBlockParser.parse(source).getBlocks();
        if (parsedBlocks.size() != 1 || !isDetailsCallout(parsedBlocks.getFirst())) {
            return null;
        }

        BlockNode details = copyBlock(parsedBlocks.getFirst());
        if (block.getId() != null && !block.getId().isBlank()) {
            details.setId(block.getId());
        }
        return List.of(details);
    }

    private Map<String, Object> detailsAttrs(DetailsOpening opening) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("kind", "details");
        attrs.put("title", opening.title());
        attrs.put("collapsible", true);
        attrs.put("open", opening.open());
        return attrs;
    }

    private String htmlSource(BlockNode block) {
        if (block == null || block.getType() != BlockType.HTML_BLOCK || block.getAttrs() == null) {
            return null;
        }

        Object sourceValue = block.getAttrs().get("source");
        return sourceValue instanceof String source ? source : null;
    }

    private String htmlText(String html) {
        if (html == null) {
            return "";
        }

        return unescapeHtml(html.replaceAll("(?is)<[^>]+>", "")).strip();
    }

    private String unescapeHtml(String value) {
        return value
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
    }

    private boolean isDetailsCallout(BlockNode block) {
        return block.getType() == BlockType.CALLOUT
                && block.getAttrs() != null
                && "details".equals(block.getAttrs().get("kind"))
                && Boolean.TRUE.equals(block.getAttrs().get("collapsible"));
    }

    private BlockNode copyBlock(BlockNode block) {
        if (block == null) {
            return null;
        }

        return BlockNode.of(
                block.getId(),
                block.getType(),
                block.getAttrs(),
                block.getInlines(),
                normalizeBlocks(block.getChildren()),
                block.getSourceRange()
        );
    }

    private List<BlockNode> normalizeBlocksForStorage(List<BlockNode> blocks, Set<String> usedBlockIds) {
        if (blocks == null || blocks.isEmpty()) {
            return new ArrayList<>();
        }

        List<BlockNode> normalized = new ArrayList<>();
        for (BlockNode block : blocks) {
            if (block == null) {
                continue;
            }
            String id = storageBlockId(block.getId(), usedBlockIds);
            usedBlockIds.add(id);
            normalized.add(BlockNode.of(
                    id,
                    block.getType(),
                    block.getAttrs(),
                    block.getInlines(),
                    normalizeBlocksForStorage(block.getChildren(), usedBlockIds),
                    block.getSourceRange()
            ));
        }
        return normalized;
    }

    private String storageBlockId(String candidate, Set<String> usedBlockIds) {
        String normalized = candidate == null ? "" : candidate.strip();
        if (!normalized.isBlank() && !isFrontendTransientBlockId(normalized) && !usedBlockIds.contains(normalized)) {
            return normalized;
        }

        String generated;
        do {
            generated = blockIdGenerator.nextId();
        } while (usedBlockIds.contains(generated));
        return generated;
    }

    private boolean isFrontendTransientBlockId(String id) {
        return id.startsWith(FRONTEND_TRANSIENT_BLOCK_ID_PREFIX)
                || id.startsWith(LEGACY_FRONTEND_TRANSIENT_BLOCK_ID_PREFIX);
    }

    private record DetailsOpening(String title, boolean open) {
    }

    private record LegacyDetailsSequence(int closeIndex, BlockNode block) {
    }

}
