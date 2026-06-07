package io.docpilot.block.processing;

import io.docpilot.block.model.BlockDocument;
import io.docpilot.block.model.BlockType;
import io.docpilot.block.model.InlineMark;
import io.docpilot.block.model.InlineNode;
import io.docpilot.block.model.InlineType;
import io.docpilot.block.model.MarkType;
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
import io.docpilot.block.typed.TypedBlockNode;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Renders DocPilot block documents back to normalized Markdown.
 */
public class MarkdownBlockRenderer {

    /**
     * Serializes a block document, preserving raw extension and HTML sources when available.
     */
    public String render(BlockDocument document) {
        return BlockNodeConverter.toTyped(document).blocks().stream()
                .map(block -> renderBlock(block, 0))
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining("\n\n"))
                .stripTrailing();
    }

    private String renderBlock(TypedBlockNode block, int depth) {
        if (block instanceof ParagraphBlock paragraph) {
            return renderInlines(paragraph.inlines());
        }
        if (block instanceof HeadingBlock heading) {
            return "#".repeat(heading.level()) + " " + renderInlines(heading.inlines());
        }
        if (block instanceof OrderedListBlock orderedList) {
            return renderList(orderedList.children(), depth, true, orderedList.start());
        }
        if (block instanceof TaskListItemBlock) {
            return renderListItem(block, depth, false, 1);
        }
        if (block instanceof CodeBlock codeBlock) {
            return renderCodeBlock(codeBlock);
        }
        if (block instanceof HtmlBlock htmlBlock) {
            return htmlBlock.source();
        }
        if (block instanceof TableCellBlock tableCell) {
            return renderInlines(tableCell.inlines());
        }
        if (block instanceof CalloutBlock callout) {
            return renderCallout(callout, depth);
        }
        if (block instanceof MathBlock mathBlock) {
            return renderMathBlock(mathBlock);
        }
        if (block instanceof DiagramBlock diagramBlock) {
            return renderDiagramBlock(diagramBlock);
        }
        if (block instanceof FrontMatterBlock frontMatter) {
            return frontMatter.raw();
        }
        if (block instanceof FootnoteDefinitionBlock footnoteDefinition) {
            return renderFootnoteDefinition(footnoteDefinition, depth);
        }
        if (block instanceof LinkReferenceDefinitionBlock linkReferenceDefinition) {
            return renderLinkReferenceDefinition(linkReferenceDefinition);
        }
        if (block instanceof TocBlock toc) {
            return toc.raw();
        }
        if (block instanceof RawBlock rawBlock) {
            return rawBlock.source().isBlank() ? rawBlock.raw() : rawBlock.source();
        }
        if (block instanceof GenericTypedBlock generic) {
            return renderGenericBlock(generic, depth);
        }
        return "";
    }

    private String renderGenericBlock(GenericTypedBlock block, int depth) {
        BlockType type = block.type();
        if (type == null) {
            return "";
        }

        return switch (type) {
            case BLOCK_QUOTE -> prefixLines(renderChildren(block.children(), depth), "> ");
            case BULLET_LIST -> renderList(block.children(), depth, false, 1);
            case LIST_ITEM -> renderListItem(block, depth, false, 1);
            case THEMATIC_BREAK -> "---";
            case TABLE -> renderTable(block.children());
            case TABLE_ROW -> renderInlines(block.children().stream().flatMap(child -> inlinesOf(child).stream()).toList());
            case DEFINITION_LIST, DOCUMENT -> renderChildren(block.children(), depth);
            case DEFINITION_TERM -> renderInlines(block.inlines());
            case DEFINITION_ITEM -> ": " + renderChildren(block.children(), depth + 1);
            default -> "";
        };
    }

    private String renderList(List<TypedBlockNode> items, int depth, boolean ordered, int start) {
        StringBuilder markdown = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                markdown.append('\n');
            }
            markdown.append(renderListItem(items.get(i), depth, ordered, start + i));
        }
        return markdown.toString();
    }

    private String renderListItem(TypedBlockNode block, int depth, boolean ordered, int number) {
        String indent = "  ".repeat(depth);
        String marker = ordered ? number + ". " : "- ";
        if (block instanceof TaskListItemBlock taskListItem) {
            marker = "- " + (taskListItem.checked() ? "[x] " : "[ ] ");
        }

        String body = renderChildren(childrenOf(block), depth + 1);
        if (body.isBlank()) {
            return indent + marker;
        }

        String[] lines = body.split("\\R", -1);
        StringBuilder markdown = new StringBuilder(indent).append(marker).append(lines[0]);
        String continuationIndent = indent + " ".repeat(marker.length());
        for (int i = 1; i < lines.length; i++) {
            markdown.append('\n').append(continuationIndent).append(lines[i]);
        }
        return markdown.toString();
    }

    private String renderChildren(List<TypedBlockNode> children, int depth) {
        return children.stream()
                .map(child -> renderBlock(child, depth))
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining("\n"));
    }

    private String renderCodeBlock(CodeBlock block) {
        return "```" + block.language() + "\n" + block.text().stripTrailing() + "\n```";
    }

    private String renderMathBlock(MathBlock block) {
        return "$$\n" + block.text().stripTrailing() + "\n$$";
    }

    private String renderDiagramBlock(DiagramBlock block) {
        return "```" + block.engine() + "\n" + block.text().stripTrailing() + "\n```";
    }

    private String renderCallout(CalloutBlock block, int depth) {
        if (block.collapsible() && "details".equalsIgnoreCase(block.kind())) {
            return renderDetails(block, depth);
        }

        String kind = block.kind().toUpperCase(Locale.ROOT);
        String title = block.title().trim();
        String header = title.isEmpty() ? "> [!" + kind + "]" : "> [!" + kind + "] " + title;
        String body = renderChildren(block.children(), depth);
        if (body.isBlank()) {
            return header;
        }
        return header + "\n" + prefixLines(body, "> ");
    }

    private String renderDetails(CalloutBlock block, int depth) {
        String body = renderChildren(block.children(), depth);
        StringBuilder markdown = new StringBuilder("<details");
        if (block.open()) {
            markdown.append(" open");
        }
        markdown.append(">\n<summary>")
                .append(escapeHtml(block.title().trim().isEmpty() ? "Details" : block.title().trim()))
                .append("</summary>");
        if (!body.isBlank()) {
            markdown.append("\n\n").append(body);
        }
        markdown.append("\n</details>");
        return markdown.toString();
    }

    private String renderFootnoteDefinition(FootnoteDefinitionBlock block, int depth) {
        String body = renderChildren(block.children(), depth + 1);
        return "[^" + block.label() + "]: " + body;
    }

    private String renderLinkReferenceDefinition(LinkReferenceDefinitionBlock block) {
        return block.title().isBlank()
                ? "[" + block.label() + "]: " + block.href()
                : "[" + block.label() + "]: " + block.href() + " \"" + block.title() + "\"";
    }

    private String renderTable(List<TypedBlockNode> rows) {
        if (rows.isEmpty()) {
            return "";
        }

        StringBuilder markdown = new StringBuilder();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            TypedBlockNode row = rows.get(rowIndex);
            String cells = childrenOf(row).stream()
                    .map(cell -> " " + renderInlines(inlinesOf(cell)) + " ")
                    .collect(Collectors.joining("|", "|", "|"));
            markdown.append(cells);
            if (rowIndex == 0) {
                markdown.append('\n');
                markdown.append(childrenOf(row).stream().map(cell -> " --- ").collect(Collectors.joining("|", "|", "|")));
            }
            if (rowIndex < rows.size() - 1) {
                markdown.append('\n');
            }
        }
        return markdown.toString();
    }

    private List<TypedBlockNode> childrenOf(TypedBlockNode block) {
        if (block instanceof OrderedListBlock orderedList) {
            return orderedList.children();
        }
        if (block instanceof TaskListItemBlock taskListItem) {
            return taskListItem.children();
        }
        if (block instanceof CalloutBlock callout) {
            return callout.children();
        }
        if (block instanceof FootnoteDefinitionBlock footnoteDefinition) {
            return footnoteDefinition.children();
        }
        if (block instanceof RawBlock rawBlock) {
            return rawBlock.children();
        }
        if (block instanceof GenericTypedBlock generic) {
            return generic.children();
        }
        return List.of();
    }

    private List<InlineNode> inlinesOf(TypedBlockNode block) {
        if (block instanceof ParagraphBlock paragraph) {
            return paragraph.inlines();
        }
        if (block instanceof HeadingBlock heading) {
            return heading.inlines();
        }
        if (block instanceof TableCellBlock tableCell) {
            return tableCell.inlines();
        }
        if (block instanceof GenericTypedBlock generic) {
            return generic.inlines();
        }
        return List.of();
    }

    private String renderInlines(List<InlineNode> inlines) {
        return inlines.stream().map(this::renderInline).collect(Collectors.joining());
    }

    private String renderInline(InlineNode inline) {
        if (inline.getType() == InlineType.IMAGE || inline.getType() == InlineType.HTML_INLINE) {
            return renderInlineText(inline);
        }

        InlineMark link = null;
        boolean code = false;
        for (InlineMark mark : inline.getMarks()) {
            if (mark.getType() == MarkType.LINK) {
                link = mark;
            } else if (mark.getType() == MarkType.CODE) {
                code = true;
            }
        }

        String text = renderInlineText(inline);
        if (code) {
            text = "`" + text + "`";
        } else {
            if (inline.getType() == InlineType.TEXT) {
                text = escapeMarkdownText(text);
            }
            for (InlineMark mark : inline.getMarks()) {
                text = applyMark(text, mark.getType());
            }
        }

        if (link != null) {
            text = "[" + text + "](" + link.getAttrs().getOrDefault(BlockAttrs.HREF.key(), "") + ")";
        }
        return text;
    }

    private String escapeMarkdownText(String text) {
        StringBuilder escaped = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            if (text.startsWith("==", index)) {
                escaped.append("\\==");
                index++;
                continue;
            }
            if (text.startsWith("++", index)) {
                escaped.append("\\++");
                index++;
                continue;
            }
            if (text.startsWith("[^", index)) {
                escaped.append("\\[^");
                index++;
                continue;
            }
            char current = text.charAt(index);
            if (isMarkdownEscapable(current)) {
                escaped.append('\\');
            }
            escaped.append(current);
        }
        return escaped.toString();
    }

    private boolean isMarkdownEscapable(char character) {
        return "\\`*_{}[]()#-~^$|<>".indexOf(character) >= 0;
    }

    private String renderInlineText(InlineNode inline) {
        InlineType type = inline.getType();
        if (type == InlineType.TEXT) {
            return inline.getText();
        }
        if (type == InlineType.SOFT_BREAK) {
            return "\n";
        }
        if (type == InlineType.HARD_BREAK) {
            return "  \n";
        }
        if (type == InlineType.IMAGE) {
            return "![" + inline.getAttrs().getOrDefault("alt", inline.getText()) + "](" + inline.getAttrs().getOrDefault("src", "") + ")";
        }
        if (type == InlineType.MATH_INLINE) {
            return "$" + inline.getText() + "$";
        }
        if (type == InlineType.FOOTNOTE_REF) {
            return "[^" + inline.getAttrs().getOrDefault(BlockAttrs.LABEL.key(), inline.getText()) + "]";
        }
        if (type == InlineType.EMOJI) {
            return String.valueOf(inline.getAttrs().getOrDefault("shortcut", inline.getText()));
        }
        if (type == InlineType.HTML_INLINE) {
            return String.valueOf(inline.getAttrs().getOrDefault(BlockAttrs.SOURCE.key(), inline.getText()));
        }
        if (type == InlineType.EXTENSION_INLINE || type == InlineType.UNSUPPORTED_INLINE) {
            return String.valueOf(inline.getAttrs().getOrDefault(BlockAttrs.SOURCE.key(), inline.getText()));
        }
        return "";
    }

    private String applyMark(String text, MarkType type) {
        if (type == MarkType.BOLD) {
            return "**" + text + "**";
        }
        if (type == MarkType.ITALIC) {
            return "*" + text + "*";
        }
        if (type == MarkType.STRIKE) {
            return "~~" + text + "~~";
        }
        if (type == MarkType.UNDERLINE) {
            return "<u>" + text + "</u>";
        }
        if (type == MarkType.INSERT) {
            return "++" + text + "++";
        }
        if (type == MarkType.SUBSCRIPT) {
            return "~" + text + "~";
        }
        if (type == MarkType.SUPERSCRIPT) {
            return "^" + text + "^";
        }
        if (type == MarkType.HIGHLIGHT) {
            return "==" + text + "==";
        }
        return text;
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String prefixLines(String value, String prefix) {
        return value.lines().map(line -> prefix + line).collect(Collectors.joining("\n"));
    }

}
