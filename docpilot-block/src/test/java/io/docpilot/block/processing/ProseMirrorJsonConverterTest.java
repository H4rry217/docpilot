package io.docpilot.block.processing;

import io.docpilot.block.model.BlockDocument;
import io.docpilot.block.model.BlockNode;
import io.docpilot.block.model.BlockType;
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

}
