package io.docpilot.block.processing;

import io.docpilot.block.model.BlockDocument;
import io.docpilot.block.model.BlockNode;
import io.docpilot.block.model.HtmlDisplayMode;
import io.docpilot.block.model.InlineMark;
import io.docpilot.block.model.InlineNode;
import io.docpilot.block.prosemirror.ProseMirrorMark;
import io.docpilot.block.prosemirror.ProseMirrorNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts DocPilot block documents to ProseMirror-compatible JSON DTO objects.
 */
public class ProseMirrorJsonConverter {

    private static final String NODE_DOC = "doc";
    private static final String NODE_PARAGRAPH = "paragraph";
    private static final String NODE_HEADING = "heading";
    private static final String NODE_BLOCKQUOTE = "blockquote";
    private static final String NODE_BULLET_LIST = "bulletList";
    private static final String NODE_ORDERED_LIST = "orderedList";
    private static final String NODE_LIST_ITEM = "listItem";
    private static final String NODE_TASK_ITEM = "taskItem";
    private static final String NODE_CODE_BLOCK = "codeBlock";
    private static final String NODE_HORIZONTAL_RULE = "horizontalRule";
    private static final String NODE_TABLE = "table";
    private static final String NODE_TABLE_ROW = "tableRow";
    private static final String NODE_TABLE_CELL = "tableCell";
    private static final String NODE_HARD_BREAK = "hardBreak";
    private static final String NODE_IMAGE = "image";
    private static final String NODE_DOCPILOT_FRONT_MATTER = "docpilotFrontMatter";
    private static final String NODE_DOCPILOT_MATH_BLOCK = "docpilotMathBlock";
    private static final String NODE_DOCPILOT_MATH_INLINE = "docpilotMathInline";
    private static final String NODE_DOCPILOT_DIAGRAM_BLOCK = "docpilotDiagramBlock";
    private static final String NODE_DOCPILOT_CALLOUT = "docpilotCallout";
    private static final String NODE_DOCPILOT_FOOTNOTE_DEFINITION = "docpilotFootnoteDefinition";
    private static final String NODE_DOCPILOT_FOOTNOTE_REF = "docpilotFootnoteRef";
    private static final String NODE_DOCPILOT_DEFINITION_LIST = "docpilotDefinitionList";
    private static final String NODE_DOCPILOT_DEFINITION_TERM = "docpilotDefinitionTerm";
    private static final String NODE_DOCPILOT_DEFINITION_ITEM = "docpilotDefinitionItem";
    private static final String NODE_DOCPILOT_TOC = "docpilotToc";
    private static final String NODE_DOCPILOT_LINK_REFERENCE_DEFINITION = "docpilotLinkReferenceDefinition";
    private static final String NODE_DOCPILOT_EMOJI = "docpilotEmoji";
    private static final String NODE_DOCPILOT_HTML_BLOCK = "docpilotHtmlBlock";
    private static final String NODE_DOCPILOT_HTML_INLINE = "docpilotHtmlInline";
    private static final String NODE_DOCPILOT_EXTENSION_BLOCK = "docpilotExtensionBlock";
    private static final String NODE_DOCPILOT_EXTENSION_INLINE = "docpilotExtensionInline";
    private static final String NODE_DOCPILOT_UNSUPPORTED_BLOCK = "docpilotUnsupportedBlock";
    private static final String NODE_DOCPILOT_UNSUPPORTED_INLINE = "docpilotUnsupportedInline";

    private static final String MARK_BOLD = "bold";
    private static final String MARK_ITALIC = "italic";
    private static final String MARK_STRIKE = "strike";
    private static final String MARK_CODE = "code";
    private static final String MARK_LINK = "link";
    private static final String MARK_UNDERLINE = "underline";
    private static final String MARK_INSERT = "insert";
    private static final String MARK_SUBSCRIPT = "subscript";
    private static final String MARK_SUPERSCRIPT = "superscript";
    private static final String MARK_HIGHLIGHT = "highlight";

    private static final String ATTR_SCHEMA_VERSION = "schemaVersion";
    private static final String ATTR_LEVEL = "level";
    private static final String ATTR_START = "start";
    private static final String ATTR_CHECKED = "checked";
    private static final String ATTR_LANGUAGE = "language";
    private static final String ATTR_TEXT = "text";
    private static final String ATTR_BLOCK_ID = "blockId";
    private static final String ATTR_SOURCE_RANGE = "sourceRange";

    /**
     * Creates a ProseMirror doc node from a DocPilot block document.
     */
    public ProseMirrorNode toProseMirror(BlockDocument document) {
        List<ProseMirrorNode> content = document.getBlocks().stream()
                .map(this::toNode)
                .toList();
        return ProseMirrorNode.node(NODE_DOC, Map.of(ATTR_SCHEMA_VERSION, document.getSchemaVersion()), content);
    }

    private ProseMirrorNode toNode(BlockNode block) {
        return switch (block.getType()) {
            case PARAGRAPH -> ProseMirrorNode.node(NODE_PARAGRAPH, sourceAttrs(block), inlineContent(block));
            case HEADING -> ProseMirrorNode.node(NODE_HEADING, withSource(block, Map.of(ATTR_LEVEL, attr(block, ATTR_LEVEL, 1))), inlineContent(block));
            case BLOCK_QUOTE -> ProseMirrorNode.node(NODE_BLOCKQUOTE, sourceAttrs(block), childContent(block));
            case BULLET_LIST -> ProseMirrorNode.node(NODE_BULLET_LIST, sourceAttrs(block), childContent(block));
            case ORDERED_LIST -> ProseMirrorNode.node(NODE_ORDERED_LIST, withSource(block, Map.of(ATTR_START, attr(block, ATTR_START, 1))), childContent(block));
            case LIST_ITEM -> ProseMirrorNode.node(NODE_LIST_ITEM, sourceAttrs(block), childContent(block));
            case TASK_LIST_ITEM -> ProseMirrorNode.node(NODE_TASK_ITEM, withSource(block, Map.of(ATTR_CHECKED, attr(block, ATTR_CHECKED, false))), childContent(block));
            case CODE_BLOCK -> ProseMirrorNode.node(NODE_CODE_BLOCK, withSource(block, Map.of(ATTR_LANGUAGE, attr(block, ATTR_LANGUAGE, ""))),
                    textContent(String.valueOf(attr(block, ATTR_TEXT, ""))));
            case THEMATIC_BREAK -> ProseMirrorNode.leaf(NODE_HORIZONTAL_RULE, sourceAttrs(block));
            case TABLE -> ProseMirrorNode.node(NODE_TABLE, sourceAttrs(block), childContent(block));
            case TABLE_ROW -> ProseMirrorNode.node(NODE_TABLE_ROW, sourceAttrs(block), childContent(block));
            case TABLE_CELL -> ProseMirrorNode.node(NODE_TABLE_CELL, withSource(block, normalizeAttrs(block.getAttrs())), inlineContent(block));
            case FRONT_MATTER -> ProseMirrorNode.leaf(NODE_DOCPILOT_FRONT_MATTER, withSource(block, normalizeAttrs(block.getAttrs())));
            case MATH_BLOCK -> ProseMirrorNode.leaf(NODE_DOCPILOT_MATH_BLOCK, withSource(block, normalizeAttrs(block.getAttrs())));
            case DIAGRAM_BLOCK -> ProseMirrorNode.leaf(NODE_DOCPILOT_DIAGRAM_BLOCK, withSource(block, normalizeAttrs(block.getAttrs())));
            case CALLOUT -> ProseMirrorNode.node(NODE_DOCPILOT_CALLOUT, withSource(block, normalizeAttrs(block.getAttrs())), childContent(block));
            case FOOTNOTE_DEFINITION -> ProseMirrorNode.node(NODE_DOCPILOT_FOOTNOTE_DEFINITION, withSource(block, normalizeAttrs(block.getAttrs())), childContent(block));
            case DEFINITION_LIST -> ProseMirrorNode.node(NODE_DOCPILOT_DEFINITION_LIST, sourceAttrs(block), childContent(block));
            case DEFINITION_TERM -> ProseMirrorNode.node(NODE_DOCPILOT_DEFINITION_TERM, withSource(block, normalizeAttrs(block.getAttrs())), inlineContent(block));
            case DEFINITION_ITEM -> ProseMirrorNode.node(NODE_DOCPILOT_DEFINITION_ITEM, withSource(block, normalizeAttrs(block.getAttrs())), childContent(block));
            case TOC -> ProseMirrorNode.leaf(NODE_DOCPILOT_TOC, withSource(block, normalizeAttrs(block.getAttrs())));
            case LINK_REFERENCE_DEFINITION -> ProseMirrorNode.leaf(NODE_DOCPILOT_LINK_REFERENCE_DEFINITION, withSource(block, normalizeAttrs(block.getAttrs())));
            case HTML_BLOCK -> ProseMirrorNode.leaf(NODE_DOCPILOT_HTML_BLOCK, withSource(block, normalizeAttrs(block.getAttrs())));
            case EXTENSION_BLOCK -> ProseMirrorNode.node(NODE_DOCPILOT_EXTENSION_BLOCK, withSource(block, normalizeAttrs(block.getAttrs())), childContent(block));
            case UNSUPPORTED_BLOCK -> ProseMirrorNode.leaf(NODE_DOCPILOT_UNSUPPORTED_BLOCK, withSource(block, normalizeAttrs(block.getAttrs())));
            case DOCUMENT -> ProseMirrorNode.node(NODE_DOC, sourceAttrs(block), childContent(block));
        };
    }

    private List<ProseMirrorNode> childContent(BlockNode block) {
        return block.getChildren().stream().map(this::toNode).toList();
    }

    private List<ProseMirrorNode> inlineContent(BlockNode block) {
        return block.getInlines().stream().map(this::toInlineNode).toList();
    }

    private ProseMirrorNode toInlineNode(InlineNode inline) {
        return switch (inline.getType()) {
            case TEXT -> ProseMirrorNode.text(inline.getText(), marks(inline.getMarks()));
            case SOFT_BREAK -> ProseMirrorNode.text("\n", marks(inline.getMarks()));
            case HARD_BREAK -> ProseMirrorNode.leaf(NODE_HARD_BREAK, sourceAttrs(inline));
            case IMAGE -> ProseMirrorNode.leaf(NODE_IMAGE, withSource(inline, normalizeAttrs(inline.getAttrs())));
            case MATH_INLINE -> ProseMirrorNode.leaf(NODE_DOCPILOT_MATH_INLINE, withSource(inline, normalizeAttrs(inline.getAttrs())));
            case FOOTNOTE_REF -> ProseMirrorNode.leaf(NODE_DOCPILOT_FOOTNOTE_REF, withSource(inline, normalizeAttrs(inline.getAttrs())));
            case EMOJI -> ProseMirrorNode.leaf(NODE_DOCPILOT_EMOJI, withSource(inline, normalizeAttrs(inline.getAttrs())));
            case HTML_INLINE -> ProseMirrorNode.leaf(NODE_DOCPILOT_HTML_INLINE, withSource(inline, normalizeAttrs(inline.getAttrs())));
            case EXTENSION_INLINE -> ProseMirrorNode.leaf(NODE_DOCPILOT_EXTENSION_INLINE, withSource(inline, normalizeAttrs(inline.getAttrs())));
            case UNSUPPORTED_INLINE -> ProseMirrorNode.leaf(NODE_DOCPILOT_UNSUPPORTED_INLINE, withSource(inline, normalizeAttrs(inline.getAttrs())));
        };
    }

    private List<ProseMirrorMark> marks(List<InlineMark> marks) {
        return marks.stream()
                .map(mark -> switch (mark.getType()) {
                    case BOLD -> ProseMirrorMark.of(MARK_BOLD, normalizeAttrs(mark.getAttrs()));
                    case ITALIC -> ProseMirrorMark.of(MARK_ITALIC, normalizeAttrs(mark.getAttrs()));
                    case STRIKE -> ProseMirrorMark.of(MARK_STRIKE, normalizeAttrs(mark.getAttrs()));
                    case CODE -> ProseMirrorMark.of(MARK_CODE, normalizeAttrs(mark.getAttrs()));
                    case LINK -> ProseMirrorMark.of(MARK_LINK, normalizeAttrs(mark.getAttrs()));
                    case UNDERLINE -> ProseMirrorMark.of(MARK_UNDERLINE, normalizeAttrs(mark.getAttrs()));
                    case INSERT -> ProseMirrorMark.of(MARK_INSERT, normalizeAttrs(mark.getAttrs()));
                    case SUBSCRIPT -> ProseMirrorMark.of(MARK_SUBSCRIPT, normalizeAttrs(mark.getAttrs()));
                    case SUPERSCRIPT -> ProseMirrorMark.of(MARK_SUPERSCRIPT, normalizeAttrs(mark.getAttrs()));
                    case HIGHLIGHT -> ProseMirrorMark.of(MARK_HIGHLIGHT, normalizeAttrs(mark.getAttrs()));
                })
                .toList();
    }

    private List<ProseMirrorNode> textContent(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        return List.of(ProseMirrorNode.text(text, List.of()));
    }

    private Map<String, Object> normalizeAttrs(Map<String, Object> attrs) {
        Map<String, Object> next = new HashMap<>();
        attrs.forEach((key, value) -> {
            if (value instanceof HtmlDisplayMode displayMode) {
                next.put(key, displayMode.getValue());
            } else {
                next.put(key, value);
            }
        });
        return next;
    }

    private Map<String, Object> sourceAttrs(BlockNode block) {
        return withSource(block, Map.of());
    }

    private Map<String, Object> sourceAttrs(InlineNode inline) {
        return withSource(inline, Map.of());
    }

    private Map<String, Object> withSource(BlockNode block, Map<String, Object> attrs) {
        Map<String, Object> next = new HashMap<>(attrs);
        next.put(ATTR_BLOCK_ID, block.getId());
        if (block.getSourceRange() != null) {
            next.put(ATTR_SOURCE_RANGE, block.getSourceRange());
        }
        return next;
    }

    private Map<String, Object> withSource(InlineNode inline, Map<String, Object> attrs) {
        Map<String, Object> next = new HashMap<>(attrs);
        if (inline.getSourceRange() != null) {
            next.put(ATTR_SOURCE_RANGE, inline.getSourceRange());
        }
        return next;
    }

    private Object attr(BlockNode block, String name, Object defaultValue) {
        return block.getAttrs().getOrDefault(name, defaultValue);
    }

}
