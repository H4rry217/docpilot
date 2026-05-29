package io.docpilot.block.processing;

import com.vladsch.flexmark.ast.AutoLink;
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
import com.vladsch.flexmark.ast.LinkRef;
import com.vladsch.flexmark.ast.ListItem;
import com.vladsch.flexmark.ast.OrderedList;
import com.vladsch.flexmark.ast.Paragraph;
import com.vladsch.flexmark.ast.Reference;
import com.vladsch.flexmark.ast.SoftLineBreak;
import com.vladsch.flexmark.ast.StrongEmphasis;
import com.vladsch.flexmark.ast.Text;
import com.vladsch.flexmark.ast.ThematicBreak;
import com.vladsch.flexmark.ext.gfm.strikethrough.Strikethrough;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListItem;
import com.vladsch.flexmark.ext.tables.TableBlock;
import com.vladsch.flexmark.ext.tables.TableBody;
import com.vladsch.flexmark.ext.tables.TableCell;
import com.vladsch.flexmark.ext.tables.TableHead;
import com.vladsch.flexmark.ext.tables.TableRow;
import com.vladsch.flexmark.ext.tables.TableSeparator;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import com.vladsch.flexmark.util.misc.Extension;
import io.docpilot.block.model.BlockDocument;
import io.docpilot.block.model.BlockNode;
import io.docpilot.block.model.BlockType;
import io.docpilot.block.model.InlineMark;
import io.docpilot.block.model.InlineNode;
import io.docpilot.block.model.InlineType;
import io.docpilot.block.model.MarkType;
import io.docpilot.block.model.SourcePosition;
import io.docpilot.block.model.SourceRange;
import io.docpilot.block.typed.BlockAttrs;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts Markdown source into DocPilot's block document model.
 */
public class MarkdownBlockParser {

    private static final Pattern FRONT_MATTER_OPEN = Pattern.compile("(?m)^---[ \\t]*\\R");
    private static final Pattern ATTRIBUTE_GROUP = Pattern.compile("\\s*\\{([^{}]+)}\\s*$");
    private static final Pattern LINK_REFERENCE = Pattern.compile("^\\[([^]]+)]\\s*:\\s*(\\S+)(?:\\s+\"([^\"]*)\")?\\s*$", Pattern.DOTALL);
    private static final Pattern FOOTNOTE_DEFINITION = Pattern.compile("^\\[\\^([^]]+)]\\s*:\\s*(.*)$", Pattern.DOTALL);
    private static final Pattern BLOCKQUOTE_CALLOUT = Pattern.compile("^>\\s*\\[!([A-Za-z][A-Za-z0-9_-]*)](.*)$");
    private static final Pattern ADMONITION_HEADER = Pattern.compile("^!!!\\s+([A-Za-z][A-Za-z0-9_-]*)(.*)$");

    private static final List<String> FLEXMARK_EXTENSION_CLASSES = List.of(
            "com.vladsch.flexmark.ext.tables.TablesExtension",
            "com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension",
            "com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughSubscriptExtension",
            "com.vladsch.flexmark.ext.autolink.AutolinkExtension",
            "com.vladsch.flexmark.ext.footnotes.FootnoteExtension",
            "com.vladsch.flexmark.ext.definition.DefinitionExtension",
            "com.vladsch.flexmark.ext.ins.InsExtension",
            "com.vladsch.flexmark.ext.gitlab.GitLabExtension",
            "com.vladsch.flexmark.ext.admonition.AdmonitionExtension",
            "com.vladsch.flexmark.ext.yaml.front.matter.YamlFrontMatterExtension",
            "com.vladsch.flexmark.ext.toc.TocExtension",
            "com.vladsch.flexmark.ext.emoji.EmojiExtension",
            "com.vladsch.flexmark.ext.attributes.AttributesExtension"
    );

    private final Parser parser;
    private final BlockIdGenerator idGenerator;

    public MarkdownBlockParser() {
        this(new BlockIdGenerator());
    }

    public MarkdownBlockParser(BlockIdGenerator idGenerator) {
        this.idGenerator = idGenerator;

        MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, loadExtensions());
        this.parser = Parser.builder(options).build();
    }

    /**
     * Parses Markdown and preserves source ranges for later AI patch positioning.
     */
    public BlockDocument parse(String markdown) {
        String source = markdown == null ? "" : markdown;
        SourceIndex sourceIndex = new SourceIndex(source);
        List<BlockNode> blocks = new ArrayList<>();

        int bodyOffset = 0;
        FrontMatterSlice frontMatter = readFrontMatter(source);
        if (frontMatter != null) {
            blocks.add(frontMatterBlock(frontMatter, sourceIndex));
            bodyOffset = frontMatter.endOffset();
        }

        String body = source.substring(bodyOffset);
        com.vladsch.flexmark.util.ast.Document document = parser.parse(body);

        int index = blocks.size();
        for (Node child : document.getChildren()) {
            BlockNode block = convertBlock(child, List.of(index), source, sourceIndex, bodyOffset);
            if (block != null) {
                blocks.add(block);
                index++;
            }
        }
        return BlockDocument.of(blocks);
    }

    private List<Extension> loadExtensions() {
        List<Extension> extensions = new ArrayList<>();
        for (String className : FLEXMARK_EXTENSION_CLASSES) {
            try {
                Class<?> extensionClass = Class.forName(className);
                Method create = extensionClass.getMethod("create");
                Object extension = create.invoke(null);
                if (extension instanceof Extension typedExtension) {
                    extensions.add(typedExtension);
                }
            } catch (ReflectiveOperationException ignored) {
                // Optional extension artifacts are still declared in Maven; this keeps older local caches usable.
            }
        }
        return extensions;
    }

    private BlockNode convertBlock(Node node, List<Integer> path, String source, SourceIndex sourceIndex, int baseOffset) {
        String id = idGenerator.nextId();
        String simpleName = node.getClass().getSimpleName();

        if (node instanceof Paragraph && isToc(node)) {
            return withTrailingAttributes(block(id, BlockType.TOC, Map.of(BlockAttrs.RAW.key(), raw(node)), List.of(), List.of(), node, sourceIndex, baseOffset), node);
        }
        if (node instanceof Paragraph && isDollarMathBlock(node)) {
            return block(id, BlockType.MATH_BLOCK, Map.of(BlockAttrs.NOTATION.key(), "latex", BlockAttrs.TEXT.key(), dollarMathBlockText(raw(node)), BlockAttrs.DELIMITER.key(), "$$"),
                    List.of(), List.of(), node, sourceIndex, baseOffset);
        }
        if (isTocBlock(node)) {
            return block(id, BlockType.TOC, Map.of(BlockAttrs.RAW.key(), raw(node)), List.of(), List.of(), node, sourceIndex, baseOffset);
        }
        if (node instanceof Reference) {
            return block(id, BlockType.LINK_REFERENCE_DEFINITION, linkReferenceAttrs(raw(node)), List.of(), List.of(), node, sourceIndex, baseOffset);
        }
        if (isFootnoteBlock(node)) {
            return block(id, BlockType.FOOTNOTE_DEFINITION, footnoteDefinitionAttrs(raw(node)), List.of(),
                    convertChildren(node, path, source, sourceIndex, baseOffset), node, sourceIndex, baseOffset);
        }
        if (isDefinitionList(node)) {
            return block(id, BlockType.DEFINITION_LIST, Map.of(), List.of(), convertChildren(node, path, source, sourceIndex, baseOffset), node, sourceIndex, baseOffset);
        }
        if (isDefinitionTerm(node)) {
            return withTrailingAttributes(block(id, BlockType.DEFINITION_TERM, Map.of(), convertInlines(node, List.of(), sourceIndex, baseOffset), List.of(), node, sourceIndex, baseOffset), node);
        }
        if (isDefinitionItem(node)) {
            return block(id, BlockType.DEFINITION_ITEM, Map.of(), List.of(), convertChildren(node, path, source, sourceIndex, baseOffset), node, sourceIndex, baseOffset);
        }
        if (isAdmonitionBlock(node)) {
            Map<String, Object> attrs = admonitionAttrs(raw(node));
            return block(id, BlockType.CALLOUT, attrs, List.of(), convertChildren(node, path, source, sourceIndex, baseOffset), node, sourceIndex, baseOffset);
        }
        if (node instanceof BlockQuote && blockQuoteCalloutAttrs(raw(node)) != null) {
            BlockNode callout = block(id, BlockType.CALLOUT, blockQuoteCalloutAttrs(raw(node)), List.of(),
                    convertChildren(node, path, source, sourceIndex, baseOffset), node, sourceIndex, baseOffset);
            stripCalloutMarker(callout);
            return callout;
        }
        if (node instanceof Paragraph) {
            return withTrailingAttributes(block(id, BlockType.PARAGRAPH, Map.of(), convertInlines(node, List.of(), sourceIndex, baseOffset), List.of(), node, sourceIndex, baseOffset), node);
        }
        if (node instanceof Heading heading) {
            return withTrailingAttributes(block(id, BlockType.HEADING, Map.of(BlockAttrs.LEVEL.key(), heading.getLevel()),
                    convertInlines(node, List.of(), sourceIndex, baseOffset), List.of(), node, sourceIndex, baseOffset), node);
        }
        if (node instanceof BlockQuote) {
            return block(id, BlockType.BLOCK_QUOTE, Map.of(), List.of(), convertChildren(node, path, source, sourceIndex, baseOffset), node, sourceIndex, baseOffset);
        }
        if (node instanceof BulletList) {
            return block(id, BlockType.BULLET_LIST, Map.of(), List.of(), convertChildren(node, path, source, sourceIndex, baseOffset), node, sourceIndex, baseOffset);
        }
        if (node instanceof OrderedList orderedList) {
            return block(id, BlockType.ORDERED_LIST, Map.of(BlockAttrs.START.key(), orderedList.getStartNumber()), List.of(),
                    convertChildren(node, path, source, sourceIndex, baseOffset), node, sourceIndex, baseOffset);
        }
        if (node instanceof TaskListItem taskListItem) {
            return block(id, BlockType.TASK_LIST_ITEM, Map.of(BlockAttrs.CHECKED.key(), taskListItem.isItemDoneMarker()), List.of(),
                    convertChildren(node, path, source, sourceIndex, baseOffset), node, sourceIndex, baseOffset);
        }
        if (node instanceof ListItem) {
            return block(id, BlockType.LIST_ITEM, Map.of(), List.of(), convertChildren(node, path, source, sourceIndex, baseOffset), node, sourceIndex, baseOffset);
        }
        if (node instanceof FencedCodeBlock fencedCodeBlock) {
            String language = fencedCodeBlock.getInfo().toString().trim();
            String text = fencedCodeBlock.getContentChars().toString();
            if ("math".equalsIgnoreCase(language)) {
                return block(id, BlockType.MATH_BLOCK, Map.of(BlockAttrs.NOTATION.key(), "latex", BlockAttrs.TEXT.key(), text, BlockAttrs.DELIMITER.key(), "fenced"), List.of(), List.of(), node, sourceIndex, baseOffset);
            }
            if ("mermaid".equalsIgnoreCase(language)) {
                return block(id, BlockType.DIAGRAM_BLOCK, Map.of(BlockAttrs.ENGINE.key(), "mermaid", BlockAttrs.TEXT.key(), text), List.of(), List.of(), node, sourceIndex, baseOffset);
            }
            Map<String, Object> attrs = new HashMap<>();
            attrs.put(BlockAttrs.LANGUAGE.key(), language);
            attrs.put(BlockAttrs.TEXT.key(), text);
            return block(id, BlockType.CODE_BLOCK, attrs, List.of(), List.of(), node, sourceIndex, baseOffset);
        }
        if (node instanceof IndentedCodeBlock) {
            return block(id, BlockType.CODE_BLOCK, Map.of(BlockAttrs.LANGUAGE.key(), "", BlockAttrs.TEXT.key(), node.getChars().toString()), List.of(), List.of(), node, sourceIndex, baseOffset);
        }
        if (node instanceof ThematicBreak) {
            return block(id, BlockType.THEMATIC_BREAK, Map.of(), List.of(), List.of(), node, sourceIndex, baseOffset);
        }
        if (node instanceof TableBlock) {
            return block(id, BlockType.TABLE, Map.of(), List.of(), convertChildren(node, path, source, sourceIndex, baseOffset), node, sourceIndex, baseOffset);
        }
        if (node instanceof TableRow) {
            return block(id, BlockType.TABLE_ROW, Map.of(), List.of(), convertChildren(node, path, source, sourceIndex, baseOffset), node, sourceIndex, baseOffset);
        }
        if (node instanceof TableSeparator) {
            return null;
        }
        if (node instanceof TableCell tableCell) {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put(BlockAttrs.HEADER.key(), tableCell.isHeader());
            attrs.put(BlockAttrs.ALIGNMENT.key(), tableCell.getAlignment() == null ? "none" : tableCell.getAlignment().name().toLowerCase(Locale.ROOT));
            return withTrailingAttributes(block(id, BlockType.TABLE_CELL, attrs, convertInlines(node, List.of(), sourceIndex, baseOffset), List.of(), node, sourceIndex, baseOffset), node);
        }
        if (node instanceof HtmlBlock) {
            String rawHtml = raw(node);
            Map<String, Object> attrs = new HashMap<>();
            attrs.put(BlockAttrs.ID.key(), id);
            attrs.put(BlockAttrs.TITLE.key(), "HTML");
            attrs.put(BlockAttrs.SOURCE.key(), rawHtml);
            attrs.put(BlockAttrs.DISPLAY_MODE.key(), "fixed");
            attrs.put(BlockAttrs.FIXED_HEIGHT_PX.key(), 320);
            attrs.put(BlockAttrs.ALLOW_SCRIPTS.key(), false);
            return block(id, BlockType.HTML_BLOCK, attrs, List.of(), List.of(), node, sourceIndex, baseOffset);
        }
        if (simpleName.endsWith("Block") && node.getClass().getName().contains(".ext.")) {
            return block(id, BlockType.EXTENSION_BLOCK, Map.of(BlockAttrs.SOURCE.key(), raw(node), BlockAttrs.NODE_TYPE.key(), simpleName), List.of(),
                    convertChildren(node, path, source, sourceIndex, baseOffset), node, sourceIndex, baseOffset);
        }

        return block(id, BlockType.UNSUPPORTED_BLOCK, Map.of(BlockAttrs.SOURCE.key(), raw(node), BlockAttrs.NODE_TYPE.key(), simpleName),
                List.of(), convertChildren(node, path, source, sourceIndex, baseOffset), node, sourceIndex, baseOffset);
    }

    private List<BlockNode> convertChildren(Node node, List<Integer> path, String source, SourceIndex sourceIndex, int baseOffset) {
        List<BlockNode> children = new ArrayList<>();
        int index = 0;
        for (Node child : node.getChildren()) {
            if (child instanceof TableSeparator) {
                continue;
            }
            if (child instanceof TableHead || child instanceof TableBody) {
                for (Node grandchild : child.getChildren()) {
                    BlockNode block = convertBlock(grandchild, append(path, index), source, sourceIndex, baseOffset);
                    if (block != null) {
                        children.add(block);
                        index++;
                    }
                }
                continue;
            }
            BlockNode block = convertBlock(child, append(path, index), source, sourceIndex, baseOffset);
            if (block != null) {
                children.add(block);
                index++;
            }
        }
        return children;
    }

    private List<InlineNode> convertInlines(Node parent, List<InlineMark> marks, SourceIndex sourceIndex, int baseOffset) {
        List<InlineNode> inlines = new ArrayList<>();
        List<InlineMark> activeMarks = new ArrayList<>(marks);
        List<Node> children = new ArrayList<>();
        for (Node child : parent.getChildren()) {
            children.add(child);
        }
        for (int index = 0; index < children.size(); index++) {
            Node child = children.get(index);
            int mathEnd = mathSequenceEnd(children, index);
            if (mathEnd > index) {
                inlines.add(inline(InlineType.MATH_INLINE, mathSequenceText(children, index, mathEnd),
                        Map.of("notation", "latex", "delimiter", "$"), activeMarks, child, sourceIndex, baseOffset));
                index = mathEnd;
                continue;
            }
            if (child instanceof HtmlInline htmlInline && isUnderlineOpen(htmlInline)) {
                activeMarks = appendMark(activeMarks, MarkType.UNDERLINE, Map.of(), child, sourceIndex, baseOffset);
                continue;
            }
            if (child instanceof HtmlInline htmlInline && isUnderlineClose(htmlInline)) {
                activeMarks = removeLastMark(activeMarks, MarkType.UNDERLINE);
                continue;
            }
            inlines.addAll(convertInline(child, activeMarks, sourceIndex, baseOffset));
        }
        return inlines;
    }

    private int mathSequenceEnd(List<Node> children, int start) {
        if (!"$".equals(raw(children.get(start)))) {
            return -1;
        }
        for (int i = start + 1; i < children.size(); i++) {
            if ("$".equals(raw(children.get(i)))) {
                return i;
            }
        }
        return -1;
    }

    private String mathSequenceText(List<Node> children, int start, int end) {
        StringBuilder text = new StringBuilder();
        for (int i = start + 1; i < end; i++) {
            text.append(raw(children.get(i)));
        }
        return text.toString();
    }

    private List<InlineNode> convertInline(Node node, List<InlineMark> marks, SourceIndex sourceIndex, int baseOffset) {
        String simpleName = node.getClass().getSimpleName();
        if (node instanceof Text) {
            return parseTextInlines(node.getChars().toString(), marks, node, sourceIndex, baseOffset);
        }
        if (node instanceof SoftLineBreak) {
            return List.of(inline(InlineType.SOFT_BREAK, "\n", Map.of(), marks, node, sourceIndex, baseOffset));
        }
        if (node instanceof HardLineBreak) {
            return List.of(inline(InlineType.HARD_BREAK, "\n", Map.of(), marks, node, sourceIndex, baseOffset));
        }
        if (node instanceof Code code) {
            return List.of(inline(InlineType.TEXT, code.getText().toString(), Map.of(),
                    appendMark(marks, MarkType.CODE, Map.of(), node, sourceIndex, baseOffset), node, sourceIndex, baseOffset));
        }
        if (node instanceof StrongEmphasis) {
            return convertInlines(node, appendMark(marks, MarkType.BOLD, Map.of(), node, sourceIndex, baseOffset), sourceIndex, baseOffset);
        }
        if (node instanceof Emphasis) {
            return convertInlines(node, appendMark(marks, MarkType.ITALIC, Map.of(), node, sourceIndex, baseOffset), sourceIndex, baseOffset);
        }
        if (node instanceof Strikethrough || "GitLabDel".equals(simpleName)) {
            return convertInlines(node, appendMark(marks, MarkType.STRIKE, Map.of(), node, sourceIndex, baseOffset), sourceIndex, baseOffset);
        }
        if ("Subscript".equals(simpleName)) {
            return convertInlines(node, appendMark(marks, MarkType.SUBSCRIPT, Map.of(), node, sourceIndex, baseOffset), sourceIndex, baseOffset);
        }
        if ("Superscript".equals(simpleName)) {
            return convertInlines(node, appendMark(marks, MarkType.SUPERSCRIPT, Map.of(), node, sourceIndex, baseOffset), sourceIndex, baseOffset);
        }
        if ("Ins".equals(simpleName) || "GitLabIns".equals(simpleName)) {
            return convertInlines(node, appendMark(marks, MarkType.INSERT, Map.of(), node, sourceIndex, baseOffset), sourceIndex, baseOffset);
        }
        if (node instanceof Link link) {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put("href", link.getUrl().toString());
            attrs.put("title", link.getTitle().toString());
            return convertInlines(node, appendMark(marks, MarkType.LINK, attrs, node, sourceIndex, baseOffset), sourceIndex, baseOffset);
        }
        if (node instanceof LinkRef linkRef) {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put("href", linkRef.getReference().toString());
            attrs.put("title", "");
            attrs.put("reference", linkRef.getReference().toString());
            return convertInlines(node, appendMark(marks, MarkType.LINK, attrs, node, sourceIndex, baseOffset), sourceIndex, baseOffset);
        }
        if (node instanceof AutoLink autoLink) {
            String text = autoLink.getText().toString();
            Map<String, Object> attrs = Map.of("href", text, "title", "");
            return List.of(inline(InlineType.TEXT, text, Map.of(), appendMark(marks, MarkType.LINK, attrs, node, sourceIndex, baseOffset), node, sourceIndex, baseOffset));
        }
        if (node instanceof Image image) {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put("src", image.getUrl().toString());
            attrs.put("title", image.getTitle().toString());
            attrs.put("alt", image.getText().toString());
            return List.of(inline(InlineType.IMAGE, image.getText().toString(), attrs, marks, node, sourceIndex, baseOffset));
        }
        if ("GitLabInlineMath".equals(simpleName) || isGitLabInlineMath(node)) {
            return List.of(inline(InlineType.MATH_INLINE, mathText(delimitedText(node)), Map.of("notation", "latex", "delimiter", "$"),
                    marks, node, sourceIndex, baseOffset));
        }
        if (isFootnoteRef(node)) {
            return List.of(inline(InlineType.FOOTNOTE_REF, "", Map.of("label", footnoteLabel(raw(node))), marks, node, sourceIndex, baseOffset));
        }
        if ("Emoji".equals(simpleName)) {
            return List.of(inline(InlineType.EMOJI, raw(node), Map.of("shortcut", raw(node)), marks, node, sourceIndex, baseOffset));
        }
        if (node instanceof HtmlInline) {
            return List.of(inline(InlineType.HTML_INLINE, raw(node), Map.of(BlockAttrs.SOURCE.key(), raw(node)), marks, node, sourceIndex, baseOffset));
        }
        if (node.hasChildren()) {
            return convertInlines(node, marks, sourceIndex, baseOffset);
        }
        if (node.getClass().getName().contains(".ext.")) {
            return List.of(inline(InlineType.EXTENSION_INLINE, raw(node),
                    Map.of(BlockAttrs.SOURCE.key(), raw(node), BlockAttrs.NODE_TYPE.key(), simpleName), marks, node, sourceIndex, baseOffset));
        }
        return List.of(inline(InlineType.UNSUPPORTED_INLINE, raw(node),
                Map.of(BlockAttrs.SOURCE.key(), raw(node), BlockAttrs.NODE_TYPE.key(), simpleName), marks, node, sourceIndex, baseOffset));
    }

    private List<InlineNode> parseTextInlines(String text, List<InlineMark> marks, Node node, SourceIndex sourceIndex, int baseOffset) {
        List<InlineNode> inlines = new ArrayList<>();
        int index = 0;
        while (index < text.length()) {
            Token token = nextToken(text, index);
            if (token == null) {
                inlines.add(inline(InlineType.TEXT, text.substring(index), Map.of(), marks, node, sourceIndex, baseOffset));
                break;
            }
            if (token.start() > index) {
                inlines.add(inline(InlineType.TEXT, text.substring(index, token.start()), Map.of(), marks, node, sourceIndex, baseOffset));
            }
            switch (token.type()) {
                case "math" -> inlines.add(inline(InlineType.MATH_INLINE, token.content(), Map.of("notation", "latex", "delimiter", "$"), marks, node, sourceIndex, baseOffset));
                case "footnote" -> inlines.add(inline(InlineType.FOOTNOTE_REF, "", Map.of("label", token.content()), marks, node, sourceIndex, baseOffset));
                case "highlight" -> inlines.addAll(parseTextInlines(token.content(), appendMark(marks, MarkType.HIGHLIGHT, Map.of(), node, sourceIndex, baseOffset), node, sourceIndex, baseOffset));
                case "insert" -> inlines.addAll(parseTextInlines(token.content(), appendMark(marks, MarkType.INSERT, Map.of(), node, sourceIndex, baseOffset), node, sourceIndex, baseOffset));
                case "superscript" -> inlines.addAll(parseTextInlines(token.content(), appendMark(marks, MarkType.SUPERSCRIPT, Map.of(), node, sourceIndex, baseOffset), node, sourceIndex, baseOffset));
                case "subscript" -> inlines.addAll(parseTextInlines(token.content(), appendMark(marks, MarkType.SUBSCRIPT, Map.of(), node, sourceIndex, baseOffset), node, sourceIndex, baseOffset));
                default -> inlines.add(inline(InlineType.TEXT, text.substring(token.start(), token.end()), Map.of(), marks, node, sourceIndex, baseOffset));
            }
            index = token.end();
        }
        return inlines;
    }

    private Token nextToken(String text, int fromIndex) {
        Token best = null;
        best = earlier(best, tokenBetween(text, fromIndex, "$", "$", "math", true));
        best = earlier(best, tokenBetween(text, fromIndex, "==", "==", "highlight", false));
        best = earlier(best, tokenBetween(text, fromIndex, "++", "++", "insert", false));
        best = earlier(best, tokenBetween(text, fromIndex, "^", "^", "superscript", false));
        best = earlier(best, tokenBetween(text, fromIndex, "~", "~", "subscript", true));
        best = earlier(best, footnoteToken(text, fromIndex));
        return best;
    }

    private Token tokenBetween(String text, int fromIndex, String open, String close, String type, boolean avoidRepeatedDelimiter) {
        int start = text.indexOf(open, fromIndex);
        while (start >= 0) {
            if (avoidRepeatedDelimiter && repeatedDelimiterAt(text, start, open)) {
                start = text.indexOf(open, start + open.length());
                continue;
            }
            int contentStart = start + open.length();
            int end = text.indexOf(close, contentStart);
            if (end > contentStart && !(avoidRepeatedDelimiter && repeatedDelimiterAt(text, end, close))) {
                return new Token(type, start, end + close.length(), text.substring(contentStart, end));
            }
            start = text.indexOf(open, contentStart);
        }
        return null;
    }

    private boolean repeatedDelimiterAt(String text, int index, String delimiter) {
        if ("$".equals(delimiter)) {
            return (index > 0 && text.charAt(index - 1) == '$') || (index + 1 < text.length() && text.charAt(index + 1) == '$');
        }
        if ("~".equals(delimiter)) {
            return (index > 0 && text.charAt(index - 1) == '~') || (index + 1 < text.length() && text.charAt(index + 1) == '~');
        }
        return false;
    }

    private Token footnoteToken(String text, int fromIndex) {
        int start = text.indexOf("[^", fromIndex);
        if (start < 0) {
            return null;
        }
        int end = text.indexOf(']', start + 2);
        if (end <= start + 2) {
            return null;
        }
        return new Token("footnote", start, end + 1, text.substring(start + 2, end));
    }

    private Token earlier(Token first, Token second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return second.start() < first.start() ? second : first;
    }

    private BlockNode block(String id, BlockType type, Map<String, Object> attrs, List<InlineNode> inlines,
                            List<BlockNode> children, Node node, SourceIndex sourceIndex, int baseOffset) {
        return BlockNode.of(id, type, attrs, inlines, children, range(node, sourceIndex, baseOffset));
    }

    private InlineNode inline(InlineType type, String text, Map<String, Object> attrs, List<InlineMark> marks,
                              Node node, SourceIndex sourceIndex, int baseOffset) {
        return InlineNode.of(type, text, attrs, marks, range(node, sourceIndex, baseOffset));
    }

    private InlineMark mark(MarkType type, Map<String, Object> attrs, Node node, SourceIndex sourceIndex, int baseOffset) {
        return InlineMark.of(type, attrs, range(node, sourceIndex, baseOffset));
    }

    private SourceRange range(Node node, SourceIndex sourceIndex, int baseOffset) {
        return SourceRange.of(sourceIndex.position(baseOffset + node.getStartOffset()), sourceIndex.position(baseOffset + node.getEndOffset()));
    }

    private SourceRange range(int startOffset, int endOffset, SourceIndex sourceIndex) {
        return SourceRange.of(sourceIndex.position(startOffset), sourceIndex.position(endOffset));
    }

    private List<Integer> append(List<Integer> path, int index) {
        List<Integer> next = new ArrayList<>(path);
        next.add(index);
        return next;
    }

    private List<InlineMark> appendMark(List<InlineMark> marks, MarkType markType, Map<String, Object> attrs,
                                        Node node, SourceIndex sourceIndex, int baseOffset) {
        List<InlineMark> next = new ArrayList<>(marks);
        next.add(mark(markType, attrs, node, sourceIndex, baseOffset));
        return next;
    }

    private List<InlineMark> removeLastMark(List<InlineMark> marks, MarkType markType) {
        List<InlineMark> next = new ArrayList<>(marks);
        for (int i = next.size() - 1; i >= 0; i--) {
            if (next.get(i).getType() == markType) {
                next.remove(i);
                break;
            }
        }
        return next;
    }

    private BlockNode frontMatterBlock(FrontMatterSlice frontMatter, SourceIndex sourceIndex) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(BlockAttrs.FORMAT.key(), "yaml");
        attrs.put(BlockAttrs.RAW.key(), frontMatter.raw());
        attrs.put(BlockAttrs.DATA.key(), parseFrontMatterData(frontMatter.content()));
        return BlockNode.of(idGenerator.nextId(), BlockType.FRONT_MATTER, attrs, List.of(), List.of(),
                range(0, frontMatter.endOffset(), sourceIndex));
    }

    private FrontMatterSlice readFrontMatter(String source) {
        Matcher open = FRONT_MATTER_OPEN.matcher(source);
        if (!open.find() || open.start() != 0) {
            return null;
        }
        int contentStart = open.end();
        int lineStart = contentStart;
        while (lineStart < source.length()) {
            int lineEnd = source.indexOf('\n', lineStart);
            int nextLineStart = lineEnd < 0 ? source.length() : lineEnd + 1;
            String line = source.substring(lineStart, lineEnd < 0 ? source.length() : lineEnd).trim();
            if ("---".equals(line) || "...".equals(line)) {
                String raw = source.substring(0, nextLineStart);
                String content = source.substring(contentStart, lineStart);
                return new FrontMatterSlice(raw, content, nextLineStart);
            }
            lineStart = nextLineStart;
        }
        return null;
    }

    private Map<String, Object> parseFrontMatterData(String content) {
        Map<String, Object> data = new LinkedHashMap<>();
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int colon = trimmed.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = trimmed.substring(0, colon).trim();
            String value = trimmed.substring(colon + 1).trim();
            data.put(key, parseScalarOrList(value));
        }
        return data;
    }

    private Object parseScalarOrList(String value) {
        if (value.startsWith("[") && value.endsWith("]")) {
            String body = value.substring(1, value.length() - 1).trim();
            if (body.isEmpty()) {
                return List.of();
            }
            List<String> values = new ArrayList<>();
            for (String item : body.split(",")) {
                values.add(unquote(item.trim()));
            }
            return values;
        }
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.parseBoolean(value);
        }
        return unquote(value);
    }

    private String unquote(String value) {
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private boolean isToc(Node node) {
        return "[TOC]".equals(raw(node).trim()) || "[[TOC]]".equals(raw(node).trim());
    }

    private boolean isDollarMathBlock(Node node) {
        String trimmed = raw(node).trim();
        return trimmed.startsWith("$$") && trimmed.endsWith("$$") && trimmed.length() > 4;
    }

    private String dollarMathBlockText(String raw) {
        String trimmed = raw.trim();
        return trimmed.substring(2, trimmed.length() - 2).strip();
    }

    private boolean isTocBlock(Node node) {
        String simpleName = node.getClass().getSimpleName();
        return "TocBlock".equals(simpleName) || "SimTocBlock".equals(simpleName);
    }

    private boolean isFootnoteBlock(Node node) {
        return "FootnoteBlock".equals(node.getClass().getSimpleName()) || FOOTNOTE_DEFINITION.matcher(raw(node).trim()).matches();
    }

    private boolean isFootnoteRef(Node node) {
        return "Footnote".equals(node.getClass().getSimpleName());
    }

    private boolean isDefinitionList(Node node) {
        return "DefinitionList".equals(node.getClass().getSimpleName());
    }

    private boolean isDefinitionTerm(Node node) {
        return "DefinitionTerm".equals(node.getClass().getSimpleName());
    }

    private boolean isDefinitionItem(Node node) {
        return "DefinitionItem".equals(node.getClass().getSimpleName());
    }

    private boolean isAdmonitionBlock(Node node) {
        return "AdmonitionBlock".equals(node.getClass().getSimpleName()) || ADMONITION_HEADER.matcher(firstLine(raw(node))).matches();
    }

    private String raw(Node node) {
        return node.getChars().toString();
    }

    private Map<String, Object> linkReferenceAttrs(String raw) {
        Matcher matcher = LINK_REFERENCE.matcher(raw.trim());
        if (!matcher.matches()) {
            return Map.of(BlockAttrs.RAW.key(), raw);
        }
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(BlockAttrs.LABEL.key(), matcher.group(1));
        attrs.put(BlockAttrs.HREF.key(), matcher.group(2));
        attrs.put(BlockAttrs.TITLE.key(), matcher.group(3) == null ? "" : matcher.group(3));
        attrs.put(BlockAttrs.RAW.key(), raw);
        return attrs;
    }

    private Map<String, Object> footnoteDefinitionAttrs(String raw) {
        Matcher matcher = FOOTNOTE_DEFINITION.matcher(raw.trim());
        if (!matcher.matches()) {
            return Map.of(BlockAttrs.LABEL.key(), "", BlockAttrs.RAW.key(), raw);
        }
        return Map.of(BlockAttrs.LABEL.key(), matcher.group(1), BlockAttrs.RAW.key(), raw);
    }

    private Map<String, Object> blockQuoteCalloutAttrs(String raw) {
        Matcher matcher = BLOCKQUOTE_CALLOUT.matcher(firstLine(raw));
        if (!matcher.matches()) {
            return null;
        }
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(BlockAttrs.KIND.key(), matcher.group(1).toLowerCase(Locale.ROOT));
        attrs.put(BlockAttrs.TITLE.key(), matcher.group(2).trim());
        attrs.put(BlockAttrs.COLLAPSIBLE.key(), false);
        attrs.put(BlockAttrs.OPEN.key(), true);
        return attrs;
    }

    private Map<String, Object> admonitionAttrs(String raw) {
        Matcher matcher = ADMONITION_HEADER.matcher(firstLine(raw));
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(BlockAttrs.KIND.key(), matcher.matches() ? matcher.group(1).toLowerCase(Locale.ROOT) : "note");
        attrs.put(BlockAttrs.TITLE.key(), matcher.matches() ? matcher.group(2).trim() : "");
        attrs.put(BlockAttrs.COLLAPSIBLE.key(), false);
        attrs.put(BlockAttrs.OPEN.key(), true);
        return attrs;
    }

    private String firstLine(String raw) {
        int newline = raw.indexOf('\n');
        return newline < 0 ? raw.trim() : raw.substring(0, newline).trim();
    }

    private void stripCalloutMarker(BlockNode callout) {
        if (callout.getChildren().isEmpty()) {
            return;
        }
        BlockNode firstChild = callout.getChildren().getFirst();
        if (firstChild.getType() != BlockType.PARAGRAPH || firstChild.getInlines().isEmpty()) {
            return;
        }
        InlineNode firstInline = firstChild.getInlines().getFirst();
        String text = firstInline.getText();
        Matcher matcher = Pattern.compile("^\\[![A-Za-z][A-Za-z0-9_-]*]\\s*").matcher(text);
        if (matcher.find()) {
            firstInline.setText(text.substring(matcher.end()));
            if (firstInline.getText().isEmpty()) {
                firstChild.getInlines().removeFirst();
            }
            if (!firstChild.getInlines().isEmpty() && firstChild.getInlines().getFirst().getType() == InlineType.SOFT_BREAK) {
                firstChild.getInlines().removeFirst();
            }
        }
    }

    private BlockNode withTrailingAttributes(BlockNode block, Node node) {
        Matcher matcher = ATTRIBUTE_GROUP.matcher(raw(node));
        if (!matcher.find()) {
            return block;
        }
        Map<String, Object> parsedAttrs = parseAttributeGroup(matcher.group(1));
        block.getAttrs().putAll(parsedAttrs);
        stripAttributeSuffix(block, matcher.group());
        return block;
    }

    private Map<String, Object> parseAttributeGroup(String body) {
        Map<String, Object> attrs = new HashMap<>();
        List<String> classNames = new ArrayList<>();
        Map<String, String> dataAttrs = new LinkedHashMap<>();
        for (String token : body.trim().split("\\s+")) {
            if (token.startsWith("#") && token.length() > 1) {
                attrs.put(BlockAttrs.HTML_ID.key(), token.substring(1));
            } else if (token.startsWith(".") && token.length() > 1) {
                classNames.add(token.substring(1));
            } else {
                int equals = token.indexOf('=');
                if (equals > 0) {
                    dataAttrs.put(token.substring(0, equals), unquote(token.substring(equals + 1)));
                }
            }
        }
        if (!classNames.isEmpty()) {
            attrs.put(BlockAttrs.CLASS_NAMES.key(), classNames);
        }
        if (!dataAttrs.isEmpty()) {
            attrs.put(BlockAttrs.DATA_ATTRS.key(), dataAttrs);
        }
        return attrs;
    }

    private void stripAttributeSuffix(BlockNode block, String suffix) {
        for (int i = block.getInlines().size() - 1; i >= 0; i--) {
            InlineNode inline = block.getInlines().get(i);
            if (inline.getType() != InlineType.TEXT) {
                continue;
            }
            String text = inline.getText();
            if (text.endsWith(suffix)) {
                inline.setText(text.substring(0, text.length() - suffix.length()).stripTrailing());
                if (inline.getText().isEmpty()) {
                    block.getInlines().remove(i);
                }
                return;
            }
        }
    }

    private boolean isUnderlineOpen(HtmlInline htmlInline) {
        return "<u>".equalsIgnoreCase(raw(htmlInline).trim());
    }

    private boolean isUnderlineClose(HtmlInline htmlInline) {
        return "</u>".equalsIgnoreCase(raw(htmlInline).trim());
    }

    private String mathText(String raw) {
        String text = raw.trim();
        if (text.startsWith("$") && text.endsWith("$") && text.length() > 1) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    private boolean isGitLabInlineMath(Node node) {
        return "GitLabInline".equals(node.getClass().getSimpleName()) && "$".equals(delimitedOpening(node));
    }

    private String delimitedOpening(Node node) {
        return invokeSequenceString(node, "getOpeningMarker", raw(node));
    }

    private String delimitedText(Node node) {
        return invokeSequenceString(node, "getText", raw(node));
    }

    private String invokeSequenceString(Node node, String methodName, String fallback) {
        try {
            Method method = node.getClass().getMethod(methodName);
            Object value = method.invoke(node);
            return value == null ? fallback : value.toString();
        } catch (ReflectiveOperationException ignored) {
            return fallback;
        }
    }

    private String footnoteLabel(String raw) {
        Matcher matcher = Pattern.compile("^\\[\\^([^]]+)]$").matcher(raw.trim());
        return matcher.matches() ? matcher.group(1) : raw;
    }

    private record FrontMatterSlice(String raw, String content, int endOffset) {
    }

    private record Token(String type, int start, int end, String content) {
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
