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

}
