package io.docpilot.workspace.knowledge;

import io.docpilot.block.model.BlockDocument;
import io.docpilot.block.model.BlockNode;
import io.docpilot.block.model.BlockType;
import io.docpilot.block.model.InlineNode;
import io.docpilot.block.model.InlineType;
import io.docpilot.workspace.knowledge.model.KnowledgeChunkDraft;
import io.docpilot.workspace.knowledge.model.KnowledgeChunkType;
import io.docpilot.workspace.knowledge.model.KnowledgeChunkingInput;
import io.docpilot.workspace.knowledge.model.KnowledgeChunkingResult;
import io.docpilot.workspace.knowledge.model.KnowledgeSectionDraft;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeChunkerTest {

    private final KnowledgeChunker chunker = new KnowledgeChunker();

    @Test
    void chunksSemanticBlocksAndTracksHeadingPath() {
        BlockDocument document = BlockDocument.of(List.of(
                heading("h1", 1, "Guide"),
                paragraph("p1", "Intro text."),
                heading("h2", 2, "Details"),
                paragraph("p2", "Detailed text."),
                code("code1", "java", "System.out.println(\"hi\");"),
                toc("toc1")
        ));

        KnowledgeChunkingInput input = new KnowledgeChunkingInput();
        input.setSnapshot(document);

        KnowledgeChunkingResult result = chunker.chunk(input);

        assertThat(result.chunks())
                .extracting(KnowledgeChunkDraft::getBlockId)
                .containsExactly("h1", "p1", "h2", "p2", "code1");
        assertThat(result.chunks())
                .extracting(KnowledgeChunkDraft::getChunkType)
                .containsOnly(KnowledgeChunkType.BLOCK);
        assertThat(result.chunks())
                .extracting(KnowledgeChunkDraft::getBlockType)
                .containsExactly("HEADING", "PARAGRAPH", "HEADING", "PARAGRAPH", "CODE_BLOCK");
        assertThat(result.chunks().get(1).getHeadingPath()).containsExactly("Guide");
        assertThat(result.chunks().get(3).getHeadingPath()).containsExactly("Guide", "Details");
        assertThat(result.chunks().get(4).getContent()).contains("```java");
        assertThat(result.chunks()).allSatisfy(chunk -> assertThat(chunk.getChunkIndex()).isZero());
        assertThat(result.sections()).isEmpty();
    }

    @Test
    void createsSectionSummaryDraftOnlyForComplexSectionTrees() {
        BlockDocument document = BlockDocument.of(List.of(
                heading("h1", 1, "Permissions"),
                paragraph("p1", "Permissions introduction."),
                heading("h2a", 2, "Roles"),
                paragraph("p2", "Role details."),
                heading("h2b", 2, "Data scope"),
                paragraph("p3", "Data scope details.")
        ));

        KnowledgeChunkingInput input = new KnowledgeChunkingInput();
        input.setSnapshot(document);

        KnowledgeChunkingResult result = chunker.chunk(input);

        assertThat(result.sections()).hasSize(1);
        assertThat(result.sections().getFirst().getHeadingBlockId()).isEqualTo("h1");
        assertThat(result.sections().getFirst().getHeadingPath()).containsExactly("Permissions");
        assertThat(result.sections().getFirst().getChunkIndex()).isZero();
        assertThat(result.sections().getFirst().getContent())
                .contains("Permissions introduction.")
                .contains("## Roles")
                .contains("Role details.")
                .contains("## Data scope")
                .contains("Data scope details.");
    }

    @Test
    void splitsLargeSectionSummaryDraftsInReadingOrder() {
        KnowledgeChunker smallPartChunker = new KnowledgeChunker(70);
        BlockDocument document = BlockDocument.of(List.of(
                heading("h1", 1, "Large section"),
                paragraph("p1", "Alpha section fact zero keeps important tail data."),
                paragraph("p2", "Beta section fact one keeps important tail data."),
                paragraph("p3", "Gamma section fact two keeps important tail data."),
                paragraph("p4", "Delta section fact three keeps important tail data."),
                paragraph("p5", "Epsilon section fact four keeps important tail data.")
        ));

        KnowledgeChunkingInput input = new KnowledgeChunkingInput();
        input.setSnapshot(document);

        KnowledgeChunkingResult result = smallPartChunker.chunk(input);

        assertThat(result.sections()).hasSize(5);
        assertThat(result.sections())
                .extracting(KnowledgeSectionDraft::getChunkIndex)
                .containsExactly(0, 1, 2, 3, 4);
        assertThat(result.sections())
                .allSatisfy(section -> assertThat(section.getContent().length()).isLessThanOrEqualTo(70));
        assertThat(result.sections().getFirst().getContent()).contains("Alpha section fact zero");
        assertThat(result.sections().getLast().getContent()).contains("Epsilon section fact four");
    }

    private BlockNode heading(String id, int level, String text) {
        return BlockNode.of(
                id,
                BlockType.HEADING,
                Map.of("level", level),
                List.of(InlineNode.of(InlineType.TEXT, text, Map.of(), List.of(), null)),
                List.of(),
                null
        );
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

    private BlockNode code(String id, String language, String text) {
        return BlockNode.of(
                id,
                BlockType.CODE_BLOCK,
                Map.of("language", language, "text", text),
                List.of(),
                List.of(),
                null
        );
    }

    private BlockNode toc(String id) {
        return BlockNode.of(id, BlockType.TOC, Map.of("raw", "[TOC]"), List.of(), List.of(), null);
    }

}
