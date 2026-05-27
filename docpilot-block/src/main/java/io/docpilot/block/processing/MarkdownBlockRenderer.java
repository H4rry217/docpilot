package io.docpilot.block.processing;

import io.docpilot.block.model.BlockDocument;
import io.docpilot.block.model.BlockNode;
import io.docpilot.block.model.BlockType;
import io.docpilot.block.model.InlineMark;
import io.docpilot.block.model.InlineNode;
import io.docpilot.block.model.InlineType;
import io.docpilot.block.model.MarkType;

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
        return document.getBlocks().stream()
                .map(block -> renderBlock(block, 0))
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining("\n\n"))
                .stripTrailing();
    }

    private String renderBlock(BlockNode block, int depth) {
        return switch (block.getType()) {
            case PARAGRAPH -> renderInlines(block.getInlines());
            case HEADING -> "#".repeat(asInt(block.getAttrs().get("level"), 1)) + " " + renderInlines(block.getInlines());
            case BLOCK_QUOTE -> prefixLines(renderChildren(block.getChildren(), depth), "> ");
            case BULLET_LIST -> renderList(block.getChildren(), depth, false, 1);
            case ORDERED_LIST -> renderList(block.getChildren(), depth, true, asInt(block.getAttrs().get("start"), 1));
            case LIST_ITEM, TASK_LIST_ITEM -> renderListItem(block, depth, false, 1);
            case CODE_BLOCK -> renderCodeBlock(block);
            case THEMATIC_BREAK -> "---";
            case TABLE -> renderTable(block);
            case TABLE_ROW -> renderInlines(block.getChildren().stream().flatMap(child -> child.getInlines().stream()).toList());
            case TABLE_CELL -> renderInlines(block.getInlines());
            case FRONT_MATTER -> String.valueOf(block.getAttrs().getOrDefault("raw", ""));
            case MATH_BLOCK -> renderMathBlock(block);
            case DIAGRAM_BLOCK -> renderDiagramBlock(block);
            case CALLOUT -> renderCallout(block, depth);
            case FOOTNOTE_DEFINITION -> renderFootnoteDefinition(block, depth);
            case DEFINITION_LIST -> renderDefinitionList(block, depth);
            case DEFINITION_TERM -> renderInlines(block.getInlines());
            case DEFINITION_ITEM -> ": " + renderChildren(block.getChildren(), depth + 1);
            case TOC -> String.valueOf(block.getAttrs().getOrDefault("raw", "[TOC]"));
            case LINK_REFERENCE_DEFINITION -> renderLinkReferenceDefinition(block);
            case HTML_BLOCK -> String.valueOf(block.getAttrs().getOrDefault("source", ""));
            case EXTENSION_BLOCK, UNSUPPORTED_BLOCK -> String.valueOf(block.getAttrs().getOrDefault("source", block.getAttrs().getOrDefault("raw", "")));
            case DOCUMENT -> renderChildren(block.getChildren(), depth);
        };
    }

    private String renderList(List<BlockNode> items, int depth, boolean ordered, int start) {
        StringBuilder markdown = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                markdown.append('\n');
            }
            markdown.append(renderListItem(items.get(i), depth, ordered, start + i));
        }
        return markdown.toString();
    }

    private String renderListItem(BlockNode block, int depth, boolean ordered, int number) {
        String indent = "  ".repeat(depth);
        String marker = ordered ? number + ". " : "- ";
        if (block.getType() == BlockType.TASK_LIST_ITEM) {
            marker = "- " + (Boolean.TRUE.equals(block.getAttrs().get("checked")) ? "[x] " : "[ ] ");
        }

        String body = renderChildren(block.getChildren(), depth + 1);
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

    private String renderChildren(List<BlockNode> children, int depth) {
        return children.stream()
                .map(child -> renderBlock(child, depth))
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining("\n"));
    }

    private String renderCodeBlock(BlockNode block) {
        String language = String.valueOf(block.getAttrs().getOrDefault("language", ""));
        String text = String.valueOf(block.getAttrs().getOrDefault("text", ""));
        return "```" + language + "\n" + text.stripTrailing() + "\n```";
    }

    private String renderMathBlock(BlockNode block) {
        String text = String.valueOf(block.getAttrs().getOrDefault("text", ""));
        return "$$\n" + text.stripTrailing() + "\n$$";
    }

    private String renderDiagramBlock(BlockNode block) {
        String engine = String.valueOf(block.getAttrs().getOrDefault("engine", "mermaid"));
        String text = String.valueOf(block.getAttrs().getOrDefault("text", ""));
        return "```" + engine + "\n" + text.stripTrailing() + "\n```";
    }

    private String renderCallout(BlockNode block, int depth) {
        String kind = String.valueOf(block.getAttrs().getOrDefault("kind", "note")).toUpperCase(Locale.ROOT);
        String title = String.valueOf(block.getAttrs().getOrDefault("title", "")).trim();
        String header = title.isEmpty() ? "> [!" + kind + "]" : "> [!" + kind + "] " + title;
        String body = renderChildren(block.getChildren(), depth);
        if (body.isBlank()) {
            return header;
        }
        return header + "\n" + prefixLines(body, "> ");
    }

    private String renderFootnoteDefinition(BlockNode block, int depth) {
        String label = String.valueOf(block.getAttrs().getOrDefault("label", ""));
        String body = renderChildren(block.getChildren(), depth + 1);
        return "[^" + label + "]: " + body;
    }

    private String renderDefinitionList(BlockNode block, int depth) {
        return block.getChildren().stream()
                .map(child -> renderBlock(child, depth))
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining("\n"));
    }

    private String renderLinkReferenceDefinition(BlockNode block) {
        String label = String.valueOf(block.getAttrs().getOrDefault("label", ""));
        String href = String.valueOf(block.getAttrs().getOrDefault("href", ""));
        String title = String.valueOf(block.getAttrs().getOrDefault("title", ""));
        return title.isBlank() ? "[" + label + "]: " + href : "[" + label + "]: " + href + " \"" + title + "\"";
    }

    private String renderTable(BlockNode table) {
        List<BlockNode> rows = table.getChildren();
        if (rows.isEmpty()) {
            return "";
        }

        StringBuilder markdown = new StringBuilder();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            BlockNode row = rows.get(rowIndex);
            String cells = row.getChildren().stream()
                    .map(cell -> " " + renderInlines(cell.getInlines()) + " ")
                    .collect(Collectors.joining("|", "|", "|"));
            markdown.append(cells);
            if (rowIndex == 0) {
                markdown.append('\n');
                markdown.append(row.getChildren().stream().map(cell -> " --- ").collect(Collectors.joining("|", "|", "|")));
            }
            if (rowIndex < rows.size() - 1) {
                markdown.append('\n');
            }
        }
        return markdown.toString();
    }

    private String renderInlines(List<InlineNode> inlines) {
        return inlines.stream().map(this::renderInline).collect(Collectors.joining());
    }

    private String renderInline(InlineNode inline) {
        String text = switch (inline.getType()) {
            case TEXT -> inline.getText();
            case SOFT_BREAK -> "\n";
            case HARD_BREAK -> "  \n";
            case IMAGE -> "![" + inline.getAttrs().getOrDefault("alt", inline.getText()) + "](" + inline.getAttrs().getOrDefault("src", "") + ")";
            case MATH_INLINE -> "$" + inline.getText() + "$";
            case FOOTNOTE_REF -> "[^" + inline.getAttrs().getOrDefault("label", inline.getText()) + "]";
            case EMOJI -> String.valueOf(inline.getAttrs().getOrDefault("shortcut", inline.getText()));
            case HTML_INLINE -> String.valueOf(inline.getAttrs().getOrDefault("source", inline.getText()));
            case EXTENSION_INLINE, UNSUPPORTED_INLINE -> String.valueOf(inline.getAttrs().getOrDefault("source", inline.getText()));
        };

        if (inline.getType() == InlineType.IMAGE || inline.getType() == InlineType.HTML_INLINE) {
            return text;
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

        if (code) {
            text = "`" + text + "`";
        } else {
            for (InlineMark mark : inline.getMarks()) {
                text = switch (mark.getType()) {
                    case BOLD -> "**" + text + "**";
                    case ITALIC -> "*" + text + "*";
                    case STRIKE -> "~~" + text + "~~";
                    case UNDERLINE -> "<u>" + text + "</u>";
                    case INSERT -> "++" + text + "++";
                    case SUBSCRIPT -> "~" + text + "~";
                    case SUPERSCRIPT -> "^" + text + "^";
                    case HIGHLIGHT -> "==" + text + "==";
                    case CODE, LINK -> text;
                };
            }
        }

        if (link != null) {
            text = "[" + text + "](" + link.getAttrs().getOrDefault("href", "") + ")";
        }
        return text;
    }

    private String prefixLines(String value, String prefix) {
        return value.lines().map(line -> prefix + line).collect(Collectors.joining("\n"));
    }

    private int asInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

}
