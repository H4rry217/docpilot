package io.docpilot.block.processing;

import com.vladsch.flexmark.ast.HtmlBlock;
import com.vladsch.flexmark.util.ast.Node;
import io.docpilot.block.model.BlockNode;
import io.docpilot.block.model.BlockType;
import io.docpilot.block.model.InlineNode;
import io.docpilot.block.model.InlineType;
import io.docpilot.block.typed.BlockAttrs;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser support for Markdown callout, admonition, details, and HTML block special cases.
 */
public final class MarkdownCalloutHtmlSupport {

    /**
     * GitHub-style blockquote callout header such as {@code > [!NOTE] Title}.
     */
    private static final Pattern BLOCKQUOTE_CALLOUT = Pattern.compile("^>\\s*\\[!([A-Za-z][A-Za-z0-9_-]*)](.*)$");

    /**
     * Flexmark admonition fallback header such as {@code !!! warning Title}.
     */
    private static final Pattern ADMONITION_HEADER = Pattern.compile("^!!!\\s+([A-Za-z][A-Za-z0-9_-]*)(.*)$");

    /**
     * Single HTML block containing a full {@code details} element.
     */
    private static final Pattern DETAILS_BLOCK = Pattern.compile("(?is)^\\s*<details\\b([^>]*)>\\s*<summary\\b[^>]*>(.*?)</summary>(.*?)</details>\\s*$");

    /**
     * Opening HTML block for a multi-node {@code details} sequence.
     */
    private static final Pattern DETAILS_OPEN_BLOCK = Pattern.compile("(?is)^\\s*<details\\b([^>]*)>\\s*(?:<summary\\b[^>]*>(.*?)</summary>)?\\s*$");

    /**
     * Closing HTML block for a multi-node {@code details} sequence.
     */
    private static final Pattern DETAILS_CLOSE_BLOCK = Pattern.compile("(?is)^\\s*</details>\\s*$");

    /**
     * Boolean {@code open} attribute detector for collapsible details.
     */
    private static final Pattern DETAILS_OPEN_ATTR = Pattern.compile("(?i)(^|\\s)open(\\s|=|$)");

    /**
     * Callout marker left in the first paragraph by blockquote parsing.
     */
    private static final Pattern CALLOUT_MARKER = Pattern.compile("^\\[![A-Za-z][A-Za-z0-9_-]*]\\s*");

    private MarkdownCalloutHtmlSupport() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Detects flexmark admonition blocks even when the extension class is not directly referenced.
     */
    public static boolean isAdmonitionBlock(Node node) {
        return "AdmonitionBlock".equals(node.getClass().getSimpleName()) || ADMONITION_HEADER.matcher(firstLine(raw(node))).matches();
    }

    /**
     * Builds callout attributes from a GitHub-style blockquote first line.
     */
    public static Map<String, Object> blockQuoteCalloutAttrs(String raw) {
        Matcher matcher = BLOCKQUOTE_CALLOUT.matcher(firstLine(raw));
        if (!matcher.matches()) {
            return null;
        }
        return calloutAttrs(matcher.group(1).toLowerCase(Locale.ROOT), matcher.group(2).trim(), false, true);
    }

    /**
     * Builds callout attributes from a flexmark admonition block.
     */
    public static Map<String, Object> admonitionAttrs(String raw) {
        Matcher matcher = ADMONITION_HEADER.matcher(firstLine(raw));
        String kind = matcher.matches() ? matcher.group(1).toLowerCase(Locale.ROOT) : "note";
        String title = matcher.matches() ? matcher.group(2).trim() : "";
        return calloutAttrs(kind, title, false, true);
    }

    /**
     * Reads a multi-node HTML details sequence from sibling flexmark nodes.
     */
    public static DetailsSequence detailsSequence(List<Node> siblings, int startIndex) {
        Node openNode = siblings.get(startIndex);
        DetailsOpening opening = detailsOpening(raw(openNode));
        if (!(openNode instanceof HtmlBlock) || opening == null) {
            return null;
        }

        for (int index = startIndex + 1; index < siblings.size(); index++) {
            Node closeNode = siblings.get(index);
            // Only a real HTML closing block terminates the details sequence; nested parsed Markdown stays in bodyNodes.
            if (closeNode instanceof HtmlBlock && DETAILS_CLOSE_BLOCK.matcher(raw(closeNode)).matches()) {
                return new DetailsSequence(opening.title(), opening.open(), siblings.subList(startIndex + 1, index), openNode, closeNode, index);
            }
        }
        return null;
    }

    /**
     * Reads a full single-node HTML details block.
     */
    public static DetailsSlice detailsSlice(String raw) {
        Matcher matcher = DETAILS_BLOCK.matcher(raw);
        if (!matcher.matches()) {
            return null;
        }
        String attrs = matcher.group(1) == null ? "" : matcher.group(1);
        String title = htmlText(matcher.group(2));
        String body = matcher.group(3) == null ? "" : matcher.group(3);
        return new DetailsSlice(title, body, matcher.start(3), DETAILS_OPEN_ATTR.matcher(attrs).find());
    }

    /**
     * Creates canonical block attributes for a DocPilot details callout.
     */
    public static Map<String, Object> detailsAttrs(String title, boolean open) {
        return calloutAttrs("details", title, true, open);
    }

    /**
     * Creates default attrs for preserved HTML blocks.
     */
    public static Map<String, Object> htmlBlockAttrs(String id, String rawHtml) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(BlockAttrs.ID.key(), id);
        attrs.put(BlockAttrs.TITLE.key(), "HTML");
        attrs.put(BlockAttrs.SOURCE.key(), rawHtml);
        attrs.put(BlockAttrs.DISPLAY_MODE.key(), "fixed");
        attrs.put(BlockAttrs.FIXED_HEIGHT_PX.key(), 320);
        attrs.put(BlockAttrs.ALLOW_SCRIPTS.key(), false);
        return attrs;
    }

    /**
     * Removes the leading callout marker from blockquote body content after the callout block is created.
     */
    public static void stripCalloutMarker(BlockNode callout) {
        if (callout.getChildren().isEmpty()) {
            return;
        }
        BlockNode firstChild = callout.getChildren().getFirst();
        if (firstChild.getType() != BlockType.PARAGRAPH || firstChild.getInlines().isEmpty()) {
            return;
        }
        InlineNode firstInline = firstChild.getInlines().getFirst();
        String text = firstInline.getText();
        Matcher matcher = CALLOUT_MARKER.matcher(text);
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

    private static DetailsOpening detailsOpening(String raw) {
        Matcher matcher = DETAILS_OPEN_BLOCK.matcher(raw);
        if (!matcher.matches()) {
            return null;
        }
        String attrs = matcher.group(1) == null ? "" : matcher.group(1);
        String title = htmlText(matcher.group(2));
        return new DetailsOpening(title, DETAILS_OPEN_ATTR.matcher(attrs).find());
    }

    private static Map<String, Object> calloutAttrs(String kind, String title, boolean collapsible, boolean open) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(BlockAttrs.KIND.key(), kind);
        attrs.put(BlockAttrs.TITLE.key(), title);
        attrs.put(BlockAttrs.COLLAPSIBLE.key(), collapsible);
        attrs.put(BlockAttrs.OPEN.key(), open);
        return attrs;
    }

    private static String htmlText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return unescapeHtml(html.replaceAll("(?is)<[^>]+>", "")).strip();
    }

    private static String unescapeHtml(String value) {
        return value
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&");
    }

    private static String firstLine(String raw) {
        int newline = raw.indexOf('\n');
        return newline < 0 ? raw.trim() : raw.substring(0, newline).trim();
    }

    private static String raw(Node node) {
        return node.getChars().toString();
    }

    /**
     * Slice data for a full details HTML block.
     *
     * @param title decoded details summary.
     * @param body inner details Markdown/HTML body.
     * @param bodyOffset source offset where the body starts within the raw HTML block.
     * @param open whether the details block starts expanded.
     */
    public record DetailsSlice(String title, String body, int bodyOffset, boolean open) {
    }

    /**
     * Multi-node details sequence data.
     *
     * @param title decoded details summary.
     * @param open whether the sequence starts expanded.
     * @param bodyNodes flexmark siblings inside the details element.
     * @param openNode opening HTML block.
     * @param closeNode closing HTML block.
     * @param endIndex sibling index of the closing node.
     */
    public record DetailsSequence(String title, boolean open, List<Node> bodyNodes, Node openNode, Node closeNode, int endIndex) {
    }

    /**
     * Parsed opening state for a details element.
     *
     * @param title decoded details summary.
     * @param open whether the opening tag contains an open attribute.
     */
    private record DetailsOpening(String title, boolean open) {
    }
}
