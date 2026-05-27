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

}
