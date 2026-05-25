package io.docpilot.block.processing;

import com.vladsch.flexmark.ast.BlockQuote;
import com.vladsch.flexmark.ast.BulletList;
import com.vladsch.flexmark.ast.Code;
import com.vladsch.flexmark.ast.Emphasis;
import com.vladsch.flexmark.ast.FencedCodeBlock;
import com.vladsch.flexmark.ast.HardLineBreak;
import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.ast.HtmlBlock;
import com.vladsch.flexmark.ast.HtmlInline;
import com.vladsch.flexmark.ast.Image;
import com.vladsch.flexmark.ast.IndentedCodeBlock;
import com.vladsch.flexmark.ast.Link;
import com.vladsch.flexmark.ast.ListItem;
import com.vladsch.flexmark.ast.OrderedList;
import com.vladsch.flexmark.ast.Paragraph;
import com.vladsch.flexmark.ast.SoftLineBreak;
import com.vladsch.flexmark.ast.StrongEmphasis;
import com.vladsch.flexmark.ast.Text;
import com.vladsch.flexmark.ast.ThematicBreak;
import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.Strikethrough;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.tables.TableBlock;
import com.vladsch.flexmark.ext.tables.TableBody;
import com.vladsch.flexmark.ext.tables.TableCell;
import com.vladsch.flexmark.ext.tables.TableHead;
import com.vladsch.flexmark.ext.tables.TableRow;
import com.vladsch.flexmark.ext.tables.TableSeparator;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListItem;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import io.docpilot.block.model.BlockDocument;
import io.docpilot.block.model.BlockNode;
import io.docpilot.block.model.BlockType;
import io.docpilot.block.model.HtmlDisplayMode;
import io.docpilot.block.model.InlineNode;
import io.docpilot.block.model.InlineType;
import io.docpilot.block.model.MarkType;
import io.docpilot.block.model.SourcePosition;
import io.docpilot.block.model.SourceRange;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts Markdown source into DocPilot's block document model.
 */
public class MarkdownBlockParser {

    private final Parser parser;
    private final BlockIdGenerator idGenerator;

    public MarkdownBlockParser() {
        this(new BlockIdGenerator());
    }

    public MarkdownBlockParser(BlockIdGenerator idGenerator) {
        this.idGenerator = idGenerator;

        MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, List.of(
                TablesExtension.create(),
                TaskListExtension.create(),
                StrikethroughExtension.create(),
                AutolinkExtension.create()
        ));
        this.parser = Parser.builder(options).build();
    }

    /**
     * Parses Markdown and preserves source ranges for later AI patch positioning.
     */
    public BlockDocument parse(String markdown) {
        String source = markdown == null ? "" : markdown;
        com.vladsch.flexmark.util.ast.Document document = parser.parse(source);
        SourceIndex sourceIndex = new SourceIndex(source);

        List<BlockNode> blocks = new ArrayList<>();
        int index = 0;
        for (Node child : document.getChildren()) {
            BlockNode block = convertBlock(child, List.of(index), source, sourceIndex);
            if (block != null) {
                blocks.add(block);
                index++;
            }
        }
        return BlockDocument.of(blocks);
    }

    private BlockNode convertBlock(Node node, List<Integer> path, String source, SourceIndex sourceIndex) {
        String id = idGenerator.nextId();

        if (node instanceof Paragraph) {
            return block(id, BlockType.PARAGRAPH, Map.of(), convertInlines(node, List.of(), sourceIndex), List.of(), node, sourceIndex);
        }
        if (node instanceof Heading heading) {
            return block(id, BlockType.HEADING, Map.of("level", heading.getLevel()),
                    convertInlines(node, List.of(), sourceIndex), List.of(), node, sourceIndex);
        }
        if (node instanceof BlockQuote) {
            return block(id, BlockType.BLOCK_QUOTE, Map.of(), List.of(), convertChildren(node, path, source, sourceIndex), node, sourceIndex);
        }
        if (node instanceof BulletList) {
            return block(id, BlockType.BULLET_LIST, Map.of(), List.of(), convertChildren(node, path, source, sourceIndex), node, sourceIndex);
        }
        if (node instanceof OrderedList orderedList) {
            return block(id, BlockType.ORDERED_LIST, Map.of("start", orderedList.getStartNumber()), List.of(),
                    convertChildren(node, path, source, sourceIndex), node, sourceIndex);
        }
        if (node instanceof TaskListItem taskListItem) {
            return block(id, BlockType.TASK_LIST_ITEM, Map.of("checked", taskListItem.isItemDoneMarker()), List.of(),
                    convertChildren(node, path, source, sourceIndex), node, sourceIndex);
        }
        if (node instanceof ListItem) {
            return block(id, BlockType.LIST_ITEM, Map.of(), List.of(), convertChildren(node, path, source, sourceIndex), node, sourceIndex);
        }
        if (node instanceof FencedCodeBlock fencedCodeBlock) {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put("language", fencedCodeBlock.getInfo().toString().trim());
            attrs.put("text", fencedCodeBlock.getContentChars().toString());
            return block(id, BlockType.CODE_BLOCK, attrs, List.of(), List.of(), node, sourceIndex);
        }
        if (node instanceof IndentedCodeBlock) {
            return block(id, BlockType.CODE_BLOCK, Map.of("language", "", "text", node.getChars().toString()), List.of(), List.of(), node, sourceIndex);
        }
        if (node instanceof ThematicBreak) {
            return block(id, BlockType.THEMATIC_BREAK, Map.of(), List.of(), List.of(), node, sourceIndex);
        }
        if (node instanceof TableBlock) {
            return block(id, BlockType.TABLE, Map.of(), List.of(), convertChildren(node, path, source, sourceIndex), node, sourceIndex);
        }
        if (node instanceof TableRow) {
            return block(id, BlockType.TABLE_ROW, Map.of(), List.of(), convertChildren(node, path, source, sourceIndex), node, sourceIndex);
        }
        if (node instanceof TableSeparator) {
            return null;
        }
        if (node instanceof TableCell tableCell) {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put("header", tableCell.isHeader());
            attrs.put("alignment", tableCell.getAlignment() == null ? "none" : tableCell.getAlignment().name().toLowerCase());
            return block(id, BlockType.TABLE_CELL, attrs, convertInlines(node, List.of(), sourceIndex), List.of(), node, sourceIndex);
        }
        if (node instanceof HtmlBlock) {
            String rawHtml = node.getChars().toString();
            Map<String, Object> attrs = new HashMap<>();
            attrs.put("id", id);
            attrs.put("title", "HTML");
            attrs.put("source", rawHtml);
            attrs.put("displayMode", HtmlDisplayMode.FIXED);
            attrs.put("fixedHeightPx", 320);
            attrs.put("allowScripts", false);
            return block(id, BlockType.HTML_BLOCK, attrs, List.of(), List.of(), node, sourceIndex);
        }

        return block(id, BlockType.UNSUPPORTED_BLOCK, Map.of("source", node.getChars().toString(), "nodeType", node.getClass().getSimpleName()),
                List.of(), convertChildren(node, path, source, sourceIndex), node, sourceIndex);
    }

    private List<BlockNode> convertChildren(Node node, List<Integer> path, String source, SourceIndex sourceIndex) {
        List<BlockNode> children = new ArrayList<>();
        int index = 0;
        for (Node child : node.getChildren()) {
            if (child instanceof TableSeparator) {
                continue;
            }
            if (child instanceof TableHead || child instanceof TableBody) {
                for (Node grandchild : child.getChildren()) {
                    List<Integer> grandchildPath = append(path, index);
                    BlockNode block = convertBlock(grandchild, grandchildPath, source, sourceIndex);
                    if (block != null) {
                        children.add(block);
                        index++;
                    }
                }
                continue;
            }
            List<Integer> childPath = append(path, index);
            BlockNode block = convertBlock(child, childPath, source, sourceIndex);
            if (block != null) {
                children.add(block);
                index++;
            }
        }
        return children;
    }

    private List<InlineNode> convertInlines(Node parent, List<MarkType> marks, SourceIndex sourceIndex) {
        List<InlineNode> inlines = new ArrayList<>();
        for (Node child : parent.getChildren()) {
            inlines.addAll(convertInline(child, marks, sourceIndex));
        }
        return inlines;
    }

    private List<InlineNode> convertInline(Node node, List<MarkType> marks, SourceIndex sourceIndex) {
        if (node instanceof Text) {
            return List.of(inline(InlineType.TEXT, node.getChars().toString(), Map.of(), marks, node, sourceIndex));
        }
        if (node instanceof SoftLineBreak) {
            return List.of(inline(InlineType.SOFT_BREAK, "\n", Map.of(), marks, node, sourceIndex));
        }
        if (node instanceof HardLineBreak) {
            return List.of(inline(InlineType.HARD_BREAK, "\n", Map.of(), marks, node, sourceIndex));
        }
        if (node instanceof Code code) {
            return List.of(inline(InlineType.CODE, code.getText().toString(), Map.of(), marks, node, sourceIndex));
        }
        if (node instanceof StrongEmphasis) {
            return convertInlines(node, appendMark(marks, MarkType.BOLD), sourceIndex);
        }
        if (node instanceof Emphasis) {
            return convertInlines(node, appendMark(marks, MarkType.ITALIC), sourceIndex);
        }
        if (node instanceof Strikethrough) {
            return convertInlines(node, appendMark(marks, MarkType.STRIKE), sourceIndex);
        }
        if (node instanceof Link link) {
            String text = plainText(node);
            Map<String, Object> attrs = new HashMap<>();
            attrs.put("href", link.getUrl().toString());
            attrs.put("title", link.getTitle().toString());
            return List.of(inline(InlineType.LINK, text, attrs, marks, node, sourceIndex));
        }
        if (node instanceof Image image) {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put("src", image.getUrl().toString());
            attrs.put("title", image.getTitle().toString());
            attrs.put("alt", image.getText().toString());
            return List.of(inline(InlineType.IMAGE, image.getText().toString(), attrs, marks, node, sourceIndex));
        }
        if (node instanceof HtmlInline) {
            return List.of(inline(InlineType.HTML_INLINE, node.getChars().toString(), Map.of("source", node.getChars().toString()), marks, node, sourceIndex));
        }
        if (node.hasChildren()) {
            return convertInlines(node, marks, sourceIndex);
        }
        return List.of(inline(InlineType.UNSUPPORTED_INLINE, node.getChars().toString(),
                Map.of("source", node.getChars().toString(), "nodeType", node.getClass().getSimpleName()), marks, node, sourceIndex));
    }

    private BlockNode block(String id, BlockType type, Map<String, Object> attrs, List<InlineNode> inlines,
                            List<BlockNode> children, Node node, SourceIndex sourceIndex) {
        return BlockNode.of(id, type, attrs, inlines, children, range(node, sourceIndex));
    }

    private InlineNode inline(InlineType type, String text, Map<String, Object> attrs, List<MarkType> marks,
                              Node node, SourceIndex sourceIndex) {
        return InlineNode.of(type, text, attrs, marks, range(node, sourceIndex));
    }

    private SourceRange range(Node node, SourceIndex sourceIndex) {
        return SourceRange.of(sourceIndex.position(node.getStartOffset()), sourceIndex.position(node.getEndOffset()));
    }

    private List<Integer> append(List<Integer> path, int index) {
        List<Integer> next = new ArrayList<>(path);
        next.add(index);
        return next;
    }

    private List<MarkType> appendMark(List<MarkType> marks, MarkType mark) {
        List<MarkType> next = new ArrayList<>(marks);
        next.add(mark);
        return next;
    }

    private String plainText(Node node) {
        StringBuilder text = new StringBuilder();
        for (Node child : node.getChildren()) {
            if (child instanceof Text) {
                text.append(child.getChars());
            } else if (child instanceof Code code) {
                text.append(code.getText());
            } else if (child.hasChildren()) {
                text.append(plainText(child));
            }
        }
        return text.toString();
    }

    private static final class SourceIndex {

        private final String source;
        private final List<Integer> lineStarts;

        private SourceIndex(String source) {
            this.source = source;
            this.lineStarts = lineStarts(source);
        }

        private SourcePosition position(int offset) {
            int safeOffset = Math.max(0, Math.min(offset, source.length()));
            int line = 0;
            for (int i = 0; i < lineStarts.size(); i++) {
                if (lineStarts.get(i) <= safeOffset) {
                    line = i;
                } else {
                    break;
                }
            }
            int column = safeOffset - lineStarts.get(line);
            return SourcePosition.of(safeOffset, line + 1, column + 1);
        }

        private static List<Integer> lineStarts(String source) {
            List<Integer> starts = new ArrayList<>();
            starts.add(0);
            for (int i = 0; i < source.length(); i++) {
                if (source.charAt(i) == '\n') {
                    starts.add(i + 1);
                }
            }
            return starts;
        }

    }

}
