package io.docpilot.block.processing;

import io.docpilot.block.model.BlockDocument;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownBlockRendererTest {

    private final MarkdownBlockParser parser = new MarkdownBlockParser();
    private final MarkdownBlockRenderer renderer = new MarkdownBlockRenderer();

    @Test
    void renderMarkdownBackFromBlockDocument() {
        BlockDocument document = parser.parse("""
                # Title

                Hello **bold**

                ```java
                System.out.println("hi");
                ```
                """);

        String markdown = renderer.render(document);

        assertThat(markdown).contains("# Title");
        assertThat(markdown).contains("**bold**");
        assertThat(markdown).contains("```java");
        assertThat(markdown).contains("System.out.println(\"hi\");");
    }

    @Test
    void renderHtmlBlockSourceAsMarkdown() {
        String source = "<div><p>hello</p></div>";
        BlockDocument document = parser.parse(source);

        assertThat(renderer.render(document)).isEqualTo(source);
    }

    @Test
    void renderEnhancedMarkdownBackFromBlockDocument() {
        BlockDocument document = parser.parse("""
                ---
                title: Spec
                ---

                > [!WARNING] Heads up
                > Body with $x$ and ==hot==.

                ```mermaid
                graph TD
                A-->B
                ```
                """);

        String markdown = renderer.render(document);

        assertThat(markdown).contains("---\ntitle: Spec\n---");
        assertThat(markdown).contains("> [!WARNING] Heads up");
        assertThat(markdown).contains("$x$");
        assertThat(markdown).contains("==hot==");
        assertThat(markdown).contains("```mermaid");
    }

    @Test
    void renderLiteralMarkdownPunctuationWithEscapes() {
        BlockDocument document = parser.parse("""
                \\#不是标题

                \\*不是斜体\\*

                \\==不是高亮\\== and \\$不是公式\\$
                """);

        String markdown = renderer.render(document);

        assertThat(markdown).contains("\\#不是标题");
        assertThat(markdown).contains("\\*不是斜体\\*");
        assertThat(markdown).contains("\\==不是高亮\\== and \\$不是公式\\$");
    }

    @Test
    void renderGithubDetailsBackFromCollapsibleCallout() {
        BlockDocument document = parser.parse("""
                <details open>
                <summary>点击展开</summary>

                隐藏内容
                </details>
                """);

        String markdown = renderer.render(document);

        assertThat(markdown).contains("<details open>");
        assertThat(markdown).contains("<summary>点击展开</summary>");
        assertThat(markdown).contains("隐藏内容");
        assertThat(markdown).contains("</details>");
    }

}
