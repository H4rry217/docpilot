package io.docpilot.block.processing;

import io.docpilot.block.model.BlockDocument;
import io.docpilot.block.model.BlockNode;
import io.docpilot.block.model.BlockType;
import io.docpilot.block.model.InlineNode;
import io.docpilot.block.model.InlineType;
import io.docpilot.block.model.MarkType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BlockDocumentNormalizerTest {

    private final BlockDocumentNormalizer normalizer = new BlockDocumentNormalizer();

    @Test
    void normalizeLegacyHtmlDetailsBlockForEditing() {
        BlockNode legacyDetails = BlockNode.of(
                "legacy-details",
                BlockType.HTML_BLOCK,
                Map.of("source", """
                        <details open>
                        <summary>Click to expand</summary>

                        Hidden **content**
                        </details>
                        """),
                List.of(),
                List.of(),
                null
        );

        BlockDocument normalized = normalizer.normalizeForEditing(BlockDocument.of(List.of(legacyDetails)));

        assertThat(normalized.getBlocks()).hasSize(1);
        BlockNode details = normalized.getBlocks().getFirst();
        assertThat(details.getId()).isEqualTo("legacy-details");
        assertThat(details.getType()).isEqualTo(BlockType.CALLOUT);
        assertThat(details.getAttrs())
                .containsEntry("kind", "details")
                .containsEntry("title", "Click to expand")
                .containsEntry("collapsible", true)
                .containsEntry("open", true);
        assertThat(details.getChildren()).hasSize(1);
        List<InlineNode> bodyInlines = details.getChildren().getFirst().getInlines();
        assertThat(bodyInlines)
                .anySatisfy(inline -> {
                    assertThat(inline.getText()).isEqualTo("content");
                    assertThat(inline.getMarks()).anySatisfy(mark -> assertThat(mark.getType()).isEqualTo(MarkType.BOLD));
                });
    }

    @Test
    void normalizeLegacySplitHtmlDetailsBlocksForEditing() {
        BlockNode opening = BlockNode.of(
                "legacy-details-open",
                BlockType.HTML_BLOCK,
                Map.of("source", """
                        <details>
                        <summary>Click to expand</summary>
                        """),
                List.of(),
                List.of(),
                null
        );
        BlockNode body = BlockNode.of(
                "details-body",
                BlockType.PARAGRAPH,
                Map.of(),
                List.of(InlineNode.of(InlineType.TEXT, "Hidden content", Map.of(), List.of(), null)),
                List.of(),
                null
        );
        BlockNode closing = BlockNode.of(
                "legacy-details-close",
                BlockType.HTML_BLOCK,
                Map.of("source", "</details>"),
                List.of(),
                List.of(),
                null
        );

        BlockDocument normalized = normalizer.normalizeForEditing(BlockDocument.of(List.of(opening, body, closing)));

        assertThat(normalized.getBlocks()).hasSize(1);
        BlockNode details = normalized.getBlocks().getFirst();
        assertThat(details.getId()).isEqualTo("legacy-details-open");
        assertThat(details.getType()).isEqualTo(BlockType.CALLOUT);
        assertThat(details.getAttrs())
                .containsEntry("kind", "details")
                .containsEntry("title", "Click to expand")
                .containsEntry("collapsible", true)
                .containsEntry("open", false);
        assertThat(details.getChildren()).hasSize(1);
        assertThat(details.getChildren().getFirst().getId()).isEqualTo("details-body");
        assertThat(details.getChildren().getFirst().getType()).isEqualTo(BlockType.PARAGRAPH);
    }

    @Test
    void keepRegularHtmlBlocksUntouched() {
        BlockNode html = BlockNode.of(
                "html",
                BlockType.HTML_BLOCK,
                Map.of("source", "<div>hello</div>"),
                List.of(),
                List.of(),
                null
        );

        BlockDocument normalized = normalizer.normalizeForEditing(BlockDocument.of(List.of(html)));

        assertThat(normalized.getBlocks()).hasSize(1);
        assertThat(normalized.getBlocks().getFirst().getType()).isEqualTo(BlockType.HTML_BLOCK);
        assertThat(normalized.getBlocks().getFirst().getAttrs()).containsEntry("source", "<div>hello</div>");
    }

    @Test
    void normalizeStorageBlockIds() {
        BlockDocumentNormalizer storageNormalizer = new BlockDocumentNormalizer(
                new MarkdownBlockParser(),
                new SequenceBlockIdGenerator("generated1", "generated2", "generated3", "generated4")
        );
        BlockDocument input = BlockDocument.of(List.of(
                paragraph("", "Missing id"),
                paragraph("existing", "Existing id"),
                paragraph("existing", "Duplicate id"),
                paragraph("docpilot-transient-local", "Current frontend transient id"),
                paragraph("frontend-local", "Legacy frontend transient id")
        ));

        BlockDocument normalized = storageNormalizer.normalizeForStorage(input);

        assertThat(normalized.getBlocks())
                .extracting(BlockNode::getId)
                .containsExactly("generated1", "existing", "generated2", "generated3", "generated4");
    }

    private BlockNode paragraph(String id, String text) {
        return BlockNode.of(
                id,
                BlockType.PARAGRAPH,
                Map.of(),
                List.of(InlineNode.of(InlineType.TEXT, text, Map.of(), List.of(), null)),
                List.of(),
                null
        );
    }

    private static class SequenceBlockIdGenerator extends BlockIdGenerator {

        private final List<String> ids;
        private int index;

        SequenceBlockIdGenerator(String... ids) {
            this.ids = List.of(ids);
        }

        @Override
        public String nextId() {
            String id = ids.get(index);
            index += 1;
            return id;
        }
    }

}
