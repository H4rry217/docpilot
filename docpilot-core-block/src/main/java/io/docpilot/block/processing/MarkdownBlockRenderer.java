package io.docpilot.block.processing;

import io.docpilot.block.model.BlockDocument;
import io.docpilot.block.model.BlockNode;
import io.docpilot.block.model.BlockType;
import io.docpilot.block.model.InlineNode;
import io.docpilot.block.model.InlineType;
import io.docpilot.block.model.MarkType;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Renders DocPilot block documents back to normalized Markdown.
 */
public class MarkdownBlockRenderer {

    /**
     * Serializes a block document, preserving raw HTML block source.
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
            case HTML_BLOCK -> String.valueOf(block.getAttrs().getOrDefault("source", ""));
            case UNSUPPORTED_BLOCK -> String.valueOf(block.getAttrs().getOrDefault("source", ""));
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
            case CODE -> "`" + inline.getText() + "`";
            case LINK -> "[" + inline.getText() + "](" + inline.getAttrs().getOrDefault("href", "") + ")";
            case IMAGE -> "![" + inline.getAttrs().getOrDefault("alt", inline.getText()) + "](" + inline.getAttrs().getOrDefault("src", "") + ")";
            case HTML_INLINE -> String.valueOf(inline.getAttrs().getOrDefault("source", inline.getText()));
            case UNSUPPORTED_INLINE -> inline.getText();
        };

        if (inline.getType() == InlineType.CODE || inline.getType() == InlineType.LINK || inline.getType() == InlineType.IMAGE) {
            return text;
        }

        for (MarkType mark : inline.getMarks()) {
            text = switch (mark) {
                case BOLD -> "**" + text + "**";
                case ITALIC -> "*" + text + "*";
                case STRIKE -> "~~" + text + "~~";
            };
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
