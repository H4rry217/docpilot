package io.docpilot.block.processing;

import io.docpilot.block.model.BlockDocument;
import io.docpilot.block.model.HtmlDisplayMode;
import io.docpilot.block.model.InlineMark;
import io.docpilot.block.model.InlineNode;
import io.docpilot.block.prosemirror.ProseMirrorMark;
import io.docpilot.block.prosemirror.ProseMirrorNode;
import io.docpilot.block.typed.BlockAttrs;
import io.docpilot.block.typed.BlockNodeConverter;
import io.docpilot.block.typed.CalloutBlock;
import io.docpilot.block.typed.CodeBlock;
import io.docpilot.block.typed.DiagramBlock;
import io.docpilot.block.typed.FootnoteDefinitionBlock;
import io.docpilot.block.typed.FrontMatterBlock;
import io.docpilot.block.typed.GenericTypedBlock;
import io.docpilot.block.typed.HeadingBlock;
import io.docpilot.block.typed.HtmlBlock;
import io.docpilot.block.typed.LinkReferenceDefinitionBlock;
import io.docpilot.block.typed.MathBlock;
import io.docpilot.block.typed.OrderedListBlock;
import io.docpilot.block.typed.ParagraphBlock;
import io.docpilot.block.typed.RawBlock;
import io.docpilot.block.typed.TableCellBlock;
import io.docpilot.block.typed.TaskListItemBlock;
import io.docpilot.block.typed.TocBlock;
import io.docpilot.block.typed.TypedBlockDocument;
import io.docpilot.block.typed.TypedBlockNode;

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
    private static final String NODE_TABLE_HEADER = "tableHeader";
    private static final String NODE_HARD_BREAK = "hardBreak";
    private static final String NODE_IMAGE = "image";
    private static final String NODE_DOCPILOT_FRONT_MATTER = "docpilotFrontMatter";
    private static final String NODE_BLOCK_MATH = "blockMath";
    private static final String NODE_INLINE_MATH = "inlineMath";
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
    private static final String ATTR_BLOCK_ID = "blockId";
    private static final String ATTR_SOURCE_RANGE = "sourceRange";

    /**
     * Creates a ProseMirror doc node from a DocPilot block document.
     */
    public ProseMirrorNode toProseMirror(BlockDocument document) {
        return toProseMirror(BlockNodeConverter.toTyped(document), document.getSchemaVersion());
    }

    public ProseMirrorNode toProseMirror(TypedBlockDocument document, String schemaVersion) {
        List<ProseMirrorNode> content = document.blocks().stream()
                .map(this::toNode)
                .toList();
        return ProseMirrorNode.node(NODE_DOC, Map.of(ATTR_SCHEMA_VERSION, schemaVersion), content);
    }

    private ProseMirrorNode toNode(TypedBlockNode block) {
        return switch (block) {
            case ParagraphBlock paragraph ->
                    ProseMirrorNode.node(NODE_PARAGRAPH, sourceAttrs(paragraph), inlineContent(paragraph.inlines()));
            case HeadingBlock heading ->
                    ProseMirrorNode.node(NODE_HEADING, withSource(heading, Map.of(BlockAttrs.LEVEL.key(), heading.level())),
                            inlineContent(heading.inlines()));
            case OrderedListBlock orderedList ->
                    ProseMirrorNode.node(NODE_ORDERED_LIST, withSource(orderedList, Map.of(BlockAttrs.START.key(), orderedList.start())),
                            childContent(orderedList.children()));
            case TaskListItemBlock taskListItem ->
                    ProseMirrorNode.node(NODE_TASK_ITEM, withSource(taskListItem, Map.of(BlockAttrs.CHECKED.key(), taskListItem.checked())),
                            childContent(taskListItem.children()));
            case CodeBlock codeBlock ->
                    ProseMirrorNode.node(NODE_CODE_BLOCK, withSource(codeBlock, attrsForCodeBlock(codeBlock)),
                            textContent(codeBlock.text()));
            case TableCellBlock tableCell -> ProseMirrorNode.node(
                    tableCell.header() ? NODE_TABLE_HEADER : NODE_TABLE_CELL,
                    withSource(tableCell, attrsForTableCell(tableCell)),
                    tableCellContent(tableCell)
            );
            case FrontMatterBlock frontMatter ->
                    ProseMirrorNode.leaf(NODE_DOCPILOT_FRONT_MATTER, withSource(frontMatter, attrsForFrontMatter(frontMatter)));
            case MathBlock mathBlock ->
                    ProseMirrorNode.leaf(NODE_BLOCK_MATH, withSource(mathBlock, attrsForMath(mathBlock)));
            case DiagramBlock diagramBlock ->
                    ProseMirrorNode.node(NODE_CODE_BLOCK, withSource(diagramBlock, attrsForDiagramBlock(diagramBlock)),
                            textContent(diagramBlock.text()));
            case CalloutBlock callout ->
                    ProseMirrorNode.node(NODE_DOCPILOT_CALLOUT, withSource(callout, attrsForCallout(callout)), childContent(callout.children()));
            case FootnoteDefinitionBlock footnoteDefinition ->
                    ProseMirrorNode.node(NODE_DOCPILOT_FOOTNOTE_DEFINITION, withSource(footnoteDefinition, attrsForFootnoteDefinition(footnoteDefinition)),
                            childContent(footnoteDefinition.children()));
            case LinkReferenceDefinitionBlock linkReferenceDefinition ->
                    ProseMirrorNode.leaf(NODE_DOCPILOT_LINK_REFERENCE_DEFINITION, withSource(linkReferenceDefinition, attrsForLinkReferenceDefinition(linkReferenceDefinition)));
            case TocBlock toc ->
                    ProseMirrorNode.leaf(NODE_DOCPILOT_TOC, withSource(toc, attrsForToc(toc)));
            case HtmlBlock htmlBlock ->
                    ProseMirrorNode.leaf(NODE_DOCPILOT_HTML_BLOCK, withSource(htmlBlock, attrsForHtml(htmlBlock)));
            case RawBlock rawBlock ->
                    rawBlock.type() == io.docpilot.block.model.BlockType.EXTENSION_BLOCK ?
                            ProseMirrorNode.node(NODE_DOCPILOT_EXTENSION_BLOCK, withSource(rawBlock, attrsForRaw(rawBlock)), childContent(rawBlock.children())) :
                            ProseMirrorNode.leaf(NODE_DOCPILOT_UNSUPPORTED_BLOCK, withSource(rawBlock, attrsForRaw(rawBlock)));
            case GenericTypedBlock generic -> toGenericNode(generic);
        };
    }

    private ProseMirrorNode toGenericNode(GenericTypedBlock block) {
        return switch (block.type()) {
            case BLOCK_QUOTE -> ProseMirrorNode.node(NODE_BLOCKQUOTE, sourceAttrs(block), childContent(block.children()));
            case BULLET_LIST -> ProseMirrorNode.node(NODE_BULLET_LIST, sourceAttrs(block), childContent(block.children()));
            case LIST_ITEM -> ProseMirrorNode.node(NODE_LIST_ITEM, sourceAttrs(block), childContent(block.children()));
            case THEMATIC_BREAK -> ProseMirrorNode.leaf(NODE_HORIZONTAL_RULE, sourceAttrs(block));
            case TABLE -> ProseMirrorNode.node(NODE_TABLE, sourceAttrs(block), childContent(block.children()));
            case TABLE_ROW -> ProseMirrorNode.node(NODE_TABLE_ROW, sourceAttrs(block), childContent(block.children()));
            case DEFINITION_LIST -> ProseMirrorNode.node(NODE_DOCPILOT_DEFINITION_LIST, sourceAttrs(block), childContent(block.children()));
            case DEFINITION_TERM -> ProseMirrorNode.node(NODE_DOCPILOT_DEFINITION_TERM, withSource(block, normalizeAttrs(block.extraAttrs())), inlineContent(block.inlines()));
            case DEFINITION_ITEM -> ProseMirrorNode.node(NODE_DOCPILOT_DEFINITION_ITEM, withSource(block, normalizeAttrs(block.extraAttrs())), childContent(block.children()));
            case DOCUMENT -> ProseMirrorNode.node(NODE_DOC, sourceAttrs(block), childContent(block.children()));
            default -> ProseMirrorNode.leaf(NODE_DOCPILOT_UNSUPPORTED_BLOCK, withSource(block, normalizeAttrs(block.extraAttrs())));
        };
    }

    private List<ProseMirrorNode> childContent(List<TypedBlockNode> blocks) {
        return blocks.stream().map(this::toNode).toList();
    }

    private List<ProseMirrorNode> inlineContent(List<InlineNode> inlines) {
        return inlines.stream().map(this::toInlineNode).toList();
    }

    private List<ProseMirrorNode> tableCellContent(TableCellBlock tableCell) {
        // TipTap table cells require block content, so inline-only cell text is wrapped in a paragraph.
        return List.of(ProseMirrorNode.node(NODE_PARAGRAPH, Map.of(), inlineContent(tableCell.inlines())));
    }

    private ProseMirrorNode toInlineNode(InlineNode inline) {
        return switch (inline.getType()) {
            case TEXT -> ProseMirrorNode.text(unescapeMarkdownText(inline.getText()), marks(inline.getMarks()));
            case SOFT_BREAK -> ProseMirrorNode.text("\n", marks(inline.getMarks()));
            case HARD_BREAK -> ProseMirrorNode.leaf(NODE_HARD_BREAK, sourceAttrs(inline));
            case IMAGE -> ProseMirrorNode.leaf(NODE_IMAGE, withSource(inline, normalizeAttrs(inline.getAttrs())));
            case MATH_INLINE -> ProseMirrorNode.leaf(NODE_INLINE_MATH, withSource(inline, attrsForInlineMath(inline)));
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

    private String unescapeMarkdownText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == '\\' && index + 1 < text.length() && isMarkdownEscapable(text.charAt(index + 1))) {
                result.append(text.charAt(index + 1));
                index++;
            } else {
                result.append(current);
            }
        }
        return result.toString();
    }

    private boolean isMarkdownEscapable(char character) {
        return character >= '!' && character <= '~' && !Character.isLetterOrDigit(character);
    }

    private Map<String, Object> attrsForCodeBlock(CodeBlock block) {
        Map<String, Object> attrs = normalizeAttrs(block.extraAttrs());
        attrs.put(BlockAttrs.LANGUAGE.key(), block.language());
        return attrs;
    }

    private Map<String, Object> attrsForDiagramBlock(DiagramBlock block) {
        Map<String, Object> attrs = normalizeAttrs(block.extraAttrs());
        attrs.put(BlockAttrs.LANGUAGE.key(), block.engine());
        return attrs;
    }

    private Map<String, Object> attrsForTableCell(TableCellBlock block) {
        Map<String, Object> attrs = normalizeAttrs(block.extraAttrs());
        attrs.put(BlockAttrs.HEADER.key(), block.header());
        attrs.put(BlockAttrs.ALIGNMENT.key(), block.alignment().value());
        return attrs;
    }

    private Map<String, Object> attrsForFrontMatter(FrontMatterBlock block) {
        Map<String, Object> attrs = normalizeAttrs(block.extraAttrs());
        attrs.put(BlockAttrs.FORMAT.key(), block.format());
        attrs.put(BlockAttrs.RAW.key(), block.raw());
        if (block.data() != null) {
            attrs.put(BlockAttrs.DATA.key(), block.data());
        }
        return attrs;
    }

    private Map<String, Object> attrsForMath(MathBlock block) {
        Map<String, Object> attrs = normalizeAttrs(block.extraAttrs());
        attrs.put(BlockAttrs.NOTATION.key(), block.notation());
        attrs.put(BlockAttrs.TEXT.key(), block.text());
        attrs.put("latex", block.text());
        attrs.put(BlockAttrs.DELIMITER.key(), block.delimiter());
        return attrs;
    }

    private Map<String, Object> attrsForInlineMath(InlineNode inline) {
        Map<String, Object> attrs = normalizeAttrs(inline.getAttrs());
        String latex = stringAttr(attrs.get("latex"));
        if (latex.isBlank()) {
            latex = stringAttr(attrs.get(BlockAttrs.TEXT.key()));
        }
        if (latex.isBlank()) {
            latex = stringAttr(attrs.get(BlockAttrs.SOURCE.key()));
        }
        if (latex.isBlank()) {
            latex = stringAttr(attrs.get(BlockAttrs.RAW.key()));
        }
        if (latex.isBlank()) {
            latex = inline.getText() == null ? "" : inline.getText();
        }
        attrs.put(BlockAttrs.NOTATION.key(), stringAttrOrDefault(attrs.get(BlockAttrs.NOTATION.key()), "latex"));
        attrs.put(BlockAttrs.TEXT.key(), latex);
        attrs.put("latex", latex);
        attrs.put(BlockAttrs.DELIMITER.key(), stringAttrOrDefault(attrs.get(BlockAttrs.DELIMITER.key()), "$"));
        return attrs;
    }

    private Map<String, Object> attrsForCallout(CalloutBlock block) {
        Map<String, Object> attrs = normalizeAttrs(block.extraAttrs());
        attrs.put(BlockAttrs.KIND.key(), block.kind());
        attrs.put(BlockAttrs.TITLE.key(), block.title());
        attrs.put(BlockAttrs.COLLAPSIBLE.key(), block.collapsible());
        attrs.put(BlockAttrs.OPEN.key(), block.open());
        return attrs;
    }

    private Map<String, Object> attrsForFootnoteDefinition(FootnoteDefinitionBlock block) {
        Map<String, Object> attrs = normalizeAttrs(block.extraAttrs());
        attrs.put(BlockAttrs.LABEL.key(), block.label());
        attrs.put(BlockAttrs.RAW.key(), block.raw());
        return attrs;
    }

    private Map<String, Object> attrsForLinkReferenceDefinition(LinkReferenceDefinitionBlock block) {
        Map<String, Object> attrs = normalizeAttrs(block.extraAttrs());
        attrs.put(BlockAttrs.LABEL.key(), block.label());
        attrs.put(BlockAttrs.HREF.key(), block.href());
        attrs.put(BlockAttrs.TITLE.key(), block.title());
        attrs.put(BlockAttrs.RAW.key(), block.raw());
        return attrs;
    }

    private Map<String, Object> attrsForToc(TocBlock block) {
        Map<String, Object> attrs = normalizeAttrs(block.extraAttrs());
        attrs.put(BlockAttrs.RAW.key(), block.raw());
        return attrs;
    }

    private Map<String, Object> attrsForHtml(HtmlBlock block) {
        Map<String, Object> attrs = normalizeAttrs(block.extraAttrs());
        attrs.put(BlockAttrs.ID.key(), block.id());
        attrs.put(BlockAttrs.TITLE.key(), block.title());
        attrs.put(BlockAttrs.SOURCE.key(), block.source());
        attrs.put(BlockAttrs.DISPLAY_MODE.key(), block.displayMode().getValue());
        attrs.put(BlockAttrs.FIXED_HEIGHT_PX.key(), block.fixedHeightPx());
        attrs.put(BlockAttrs.ALLOW_SCRIPTS.key(), block.allowScripts());
        return attrs;
    }

    private Map<String, Object> attrsForRaw(RawBlock block) {
        Map<String, Object> attrs = normalizeAttrs(block.extraAttrs());
        putIfNotBlank(attrs, BlockAttrs.SOURCE.key(), block.source());
        putIfNotBlank(attrs, BlockAttrs.RAW.key(), block.raw());
        putIfNotBlank(attrs, BlockAttrs.NODE_TYPE.key(), block.nodeType());
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

    private Map<String, Object> sourceAttrs(TypedBlockNode block) {
        return withSource(block, Map.of());
    }

    private Map<String, Object> sourceAttrs(InlineNode inline) {
        return withSource(inline, Map.of());
    }

    private Map<String, Object> withSource(TypedBlockNode block, Map<String, Object> attrs) {
        Map<String, Object> next = new HashMap<>(attrs);
        next.put(ATTR_BLOCK_ID, block.id());
        if (block.sourceRange() != null) {
            next.put(ATTR_SOURCE_RANGE, block.sourceRange());
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

    private void putIfNotBlank(Map<String, Object> attrs, String key, String value) {
        if (value != null && !value.isBlank()) {
            attrs.put(key, value);
        }
    }

    private String stringAttr(Object value) {
        return value instanceof String text ? text : "";
    }

    private String stringAttrOrDefault(Object value, String fallback) {
        String text = stringAttr(value);
        return text.isBlank() ? fallback : text;
    }

}
