package io.docpilot.block.processing;

import io.docpilot.block.model.BlockDocument;
import io.docpilot.block.model.BlockNode;
import io.docpilot.block.model.BlockType;
import io.docpilot.block.model.InlineMark;
import io.docpilot.block.model.InlineNode;
import io.docpilot.block.model.InlineType;
import io.docpilot.block.model.MarkType;
import io.docpilot.block.prosemirror.ProseMirrorNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProseMirrorJsonConverterTest {

    private final ProseMirrorJsonConverter converter = new ProseMirrorJsonConverter();

    @Test
    void convertDocumentToProseMirrorDoc() {
        BlockDocument document = new MarkdownBlockParser().parse("# Title\n\nHello **DocPilot**");

        ProseMirrorNode node = converter.toProseMirror(document);

        assertThat(node.getType()).isEqualTo("doc");
        assertThat(node.getContent()).extracting(ProseMirrorNode::getType).containsExactly("heading", "paragraph");
        assertThat(node.getContent().get(1).getContent())
                .anySatisfy(child -> {
                    assertThat(child.getType()).isEqualTo("text");
                    assertThat(child.getMarks()).anySatisfy(mark -> assertThat(mark.getType()).isEqualTo("bold"));
                });
    }

    @Test
    void convertHtmlBlockToDocPilotHtmlBlockNode() {
        BlockNode html = BlockNode.of(
                "1234567890abcdef1234567890abcdef",
                BlockType.HTML_BLOCK,
                Map.of(
                        "id", "1234567890abcdef1234567890abcdef",
                        "title", "HTML",
                        "source", "<div>hello</div>",
                        "displayMode", "fixed",
                        "fixedHeightPx", 320,
                        "allowScripts", false
                ),
                List.of(),
                List.of(),
                null
        );

        ProseMirrorNode node = converter.toProseMirror(BlockDocument.of(List.of(html))).getContent().getFirst();

        assertThat(node.getType()).isEqualTo("docpilotHtmlBlock");
        assertThat(node.getAttrs())
                .containsEntry("id", "1234567890abcdef1234567890abcdef")
                .containsEntry("title", "HTML")
                .containsEntry("source", "<div>hello</div>")
                .containsEntry("displayMode", "fixed")
                .containsEntry("fixedHeightPx", 320)
                .containsEntry("allowScripts", false);
        assertThat(node.getAttrs()).doesNotContainKeys("plainText", "syncId");
    }

    @Test
    void unescapeMarkdownPunctuationStoredInLegacyTextInlines() {
        BlockNode paragraph = BlockNode.of(
                "paragraph1",
                BlockType.PARAGRAPH,
                Map.of(),
                List.of(InlineNode.of(InlineType.TEXT, "\\*不是斜体\\*", Map.of(), List.of(), null)),
                List.of(),
                null
        );

        ProseMirrorNode text = converter.toProseMirror(BlockDocument.of(List.of(paragraph)))
                .getContent().getFirst()
                .getContent().getFirst();

        assertThat(text.getText()).isEqualTo("*不是斜体*");
    }

    @Test
    void convertEnhancedNodesAndMarksToProseMirror() {
        BlockNode paragraph = BlockNode.of(
                "paragraph1",
                BlockType.PARAGRAPH,
                Map.of(),
                List.of(
                        InlineNode.of(InlineType.TEXT, "DocPilot", Map.of(),
                                List.of(
                                        InlineMark.of(MarkType.BOLD),
                                        InlineMark.of(MarkType.LINK, Map.of("href", "https://example.com", "title", ""))
                                ),
                                null),
                        InlineNode.of(InlineType.MATH_INLINE, "x^2", Map.of("notation", "latex", "delimiter", "$"), List.of(), null),
                        InlineNode.of(InlineType.FOOTNOTE_REF, "", Map.of("label", "one"), List.of(), null)
                ),
                List.of(),
                null
        );
        BlockNode math = BlockNode.of(
                "math1",
                BlockType.MATH_BLOCK,
                Map.of("notation", "latex", "text", "x^2", "delimiter", "$$"),
                List.of(),
                List.of(),
                null
        );
        BlockNode diagram = BlockNode.of(
                "diagram1",
                BlockType.DIAGRAM_BLOCK,
                Map.of("engine", "mermaid", "text", "graph TD", "caption", "Request flow", "width", 420),
                List.of(),
                List.of(),
                null
        );

        ProseMirrorNode doc = converter.toProseMirror(BlockDocument.of(List.of(paragraph, math, diagram)));

        assertThat(doc.getContent()).extracting(ProseMirrorNode::getType)
                .containsExactly("paragraph", "docpilotMathBlock", "codeBlock");
        assertThat(doc.getContent().get(2).getAttrs()).containsEntry("language", "mermaid");
        assertThat(doc.getContent().get(2).getAttrs())
                .containsEntry("caption", "Request flow")
                .containsEntry("width", 420);
        assertThat(doc.getContent().get(2).getContent().getFirst().getText()).isEqualTo("graph TD");
        assertThat(doc.getContent().getFirst().getContent())
                .anySatisfy(child -> {
                    assertThat(child.getType()).isEqualTo("text");
                    assertThat(child.getMarks()).extracting(mark -> mark.getType()).contains("bold", "link");
                })
                .anySatisfy(child -> assertThat(child.getType()).isEqualTo("docpilotMathInline"))
                .anySatisfy(child -> assertThat(child.getType()).isEqualTo("docpilotFootnoteRef"));
    }

    @Test
    void preserveCodeBlockExtraAttrsWhenConvertingToProseMirror() {
        BlockNode mermaid = BlockNode.of(
                "mermaid1",
                BlockType.CODE_BLOCK,
                Map.of(
                        "language", "mermaid",
                        "text", "graph TD\n  A-->B",
                        "caption", "Request flow",
                        "width", 420
                ),
                List.of(),
                List.of(),
                null
        );

        ProseMirrorNode node = converter.toProseMirror(BlockDocument.of(List.of(mermaid))).getContent().getFirst();

        assertThat(node.getType()).isEqualTo("codeBlock");
        assertThat(node.getAttrs())
                .containsEntry("language", "mermaid")
                .containsEntry("caption", "Request flow")
                .containsEntry("width", 420);
        assertThat(node.getContent().getFirst().getText()).isEqualTo("graph TD\n  A-->B");
    }

    @Test
    void convertMathTextWithoutAddingBackslashes() {
        BlockNode math = BlockNode.of(
                "math1",
                BlockType.MATH_BLOCK,
                Map.of("notation", "latex", "text", "\\int_a^b f(x)dx", "delimiter", "$$"),
                List.of(),
                List.of(),
                null
        );

        ProseMirrorNode node = converter.toProseMirror(BlockDocument.of(List.of(math))).getContent().getFirst();

        assertThat(node.getType()).isEqualTo("docpilotMathBlock");
        assertThat(node.getAttrs()).containsEntry("text", "\\int_a^b f(x)dx");
    }

}
