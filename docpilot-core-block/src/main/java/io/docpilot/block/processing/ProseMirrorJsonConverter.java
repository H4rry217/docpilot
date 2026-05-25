package io.docpilot.block.processing;

import io.docpilot.block.model.BlockDocument;
import io.docpilot.block.model.BlockNode;
import io.docpilot.block.model.HtmlDisplayMode;
import io.docpilot.block.model.InlineNode;
import io.docpilot.block.model.MarkType;
import io.docpilot.block.prosemirror.ProseMirrorMark;
import io.docpilot.block.prosemirror.ProseMirrorNode;

import java.util.ArrayList;
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
    private static final String NODE_DOCPILOT_HTML_BLOCK = "docpilotHtmlBlock";
    private static final String NODE_DOCPILOT_HTML_INLINE = "docpilotHtmlInline";
    private static final String NODE_DOCPILOT_UNSUPPORTED_BLOCK = "docpilotUnsupportedBlock";
    private static final String NODE_DOCPILOT_UNSUPPORTED_INLINE = "docpilotUnsupportedInline";

    private static final String MARK_BOLD = "bold";
    private static final String MARK_ITALIC = "italic";
    private static final String MARK_STRIKE = "strike";
    private static final String MARK_CODE = "code";
    private static final String MARK_LINK = "link";

    private static final String ATTR_SCHEMA_VERSION = "schemaVersion";
    private static final String ATTR_LEVEL = "level";
    private static final String ATTR_START = "start";
    private static final String ATTR_CHECKED = "checked";
    private static final String ATTR_LANGUAGE = "language";
    private static final String ATTR_TEXT = "text";
    private static final String ATTR_HREF = "href";
    private static final String ATTR_TITLE = "title";
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
            case HTML_BLOCK -> ProseMirrorNode.leaf(NODE_DOCPILOT_HTML_BLOCK, withSource(block, normalizeAttrs(block.getAttrs())));
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
            case CODE -> ProseMirrorNode.text(inline.getText(), appendMark(marks(inline.getMarks()), ProseMirrorMark.of(MARK_CODE)));
            case LINK -> ProseMirrorNode.text(inline.getText(), appendMark(marks(inline.getMarks()), ProseMirrorMark.of(MARK_LINK, linkAttrs(inline))));
            case IMAGE -> ProseMirrorNode.leaf(NODE_IMAGE, withSource(inline, normalizeAttrs(inline.getAttrs())));
            case HTML_INLINE -> ProseMirrorNode.leaf(NODE_DOCPILOT_HTML_INLINE, withSource(inline, normalizeAttrs(inline.getAttrs())));
            case UNSUPPORTED_INLINE -> ProseMirrorNode.leaf(NODE_DOCPILOT_UNSUPPORTED_INLINE, withSource(inline, normalizeAttrs(inline.getAttrs())));
        };
    }

    private List<ProseMirrorMark> marks(List<MarkType> marks) {
        return marks.stream()
                .map(mark -> switch (mark) {
                    case BOLD -> ProseMirrorMark.of(MARK_BOLD);
                    case ITALIC -> ProseMirrorMark.of(MARK_ITALIC);
                    case STRIKE -> ProseMirrorMark.of(MARK_STRIKE);
                })
                .toList();
    }

    private List<ProseMirrorMark> appendMark(List<ProseMirrorMark> marks, ProseMirrorMark mark) {
        List<ProseMirrorMark> next = new ArrayList<>(marks);
        next.add(mark);
        return next;
    }

    private List<ProseMirrorNode> textContent(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        return List.of(ProseMirrorNode.text(text, List.of()));
    }

    private Map<String, Object> linkAttrs(InlineNode inline) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(ATTR_HREF, inline.getAttrs().getOrDefault(ATTR_HREF, ""));
        attrs.put(ATTR_TITLE, inline.getAttrs().getOrDefault(ATTR_TITLE, ""));
        return attrs;
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
