package io.docpilot.workspace.knowledge;

import io.docpilot.ai.AiEmbeddingModel;
import io.docpilot.ai.AiEmbeddingRegistry;
import io.docpilot.ai.AiModelMetadata;
import io.docpilot.ai.model.EmbeddingRequest;
import io.docpilot.ai.model.EmbeddingResponse;
import io.docpilot.block.model.BlockDocument;
import io.docpilot.block.model.BlockNode;
import io.docpilot.block.model.BlockType;
import io.docpilot.block.model.InlineNode;
import io.docpilot.block.model.InlineType;
import io.docpilot.workspace.knowledge.config.KnowledgeProperties;
import io.docpilot.workspace.knowledge.model.KnowledgeChunkDraft;
import io.docpilot.workspace.knowledge.model.KnowledgeChunkType;
import io.docpilot.workspace.knowledge.model.KnowledgeIndexedChunk;
import io.docpilot.workspace.knowledge.model.KnowledgeSectionDraft;
import io.docpilot.workspace.knowledge.store.KnowledgeChunkStore;
import io.docpilot.workspace.knowledge.store.KnowledgeSearchQuery;
import io.docpilot.workspace.model.entity.DocumentRevision;
import io.docpilot.workspace.model.entity.WorkspaceDocument;
import io.docpilot.workspace.repository.DocumentRevisionRepository;
import io.docpilot.workspace.repository.WorkspaceDocumentRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeIndexCommandHandlerTest {

    @Test
    void indexesCurrentRevisionWithSummaryAndEmbeddings() {
        InMemoryDocumentRepository documents = new InMemoryDocumentRepository();
        InMemoryRevisionRepository revisions = new InMemoryRevisionRepository();
        CapturingStore store = new CapturingStore();

        WorkspaceDocument document = document(10L, 20L, 30L, 100L);
        DocumentRevision revision = revision(100L, 30L, BlockDocument.of(List.of(
                heading("h1", 1, "Refund"),
                paragraph("p1", "Refunds are available within seven days."),
                heading("h2a", 2, "Window"),
                paragraph("p2", "The refund window is seven days."),
                heading("h2b", 2, "Exceptions"),
                paragraph("p3", "Digital goods are excluded.")
        )));
        documents.save(document);
        revisions.save(revision);

        handler(documents, revisions, store).indexDocumentRevision(30L, 100L);

        assertThat(store.workspaceId).isEqualTo(20L);
        assertThat(store.documentId).isEqualTo(30L);
        assertThat(store.chunks).hasSize(7);
        assertThat(store.chunks)
                .extracting(KnowledgeIndexedChunk::getChunkType)
                .containsExactly("BLOCK", "BLOCK", "BLOCK", "BLOCK", "BLOCK", "BLOCK", "SECTION_SUMMARY");
        assertThat(store.chunks)
                .extracting(KnowledgeIndexedChunk::getBlockType)
                .containsExactly("HEADING", "PARAGRAPH", "HEADING", "PARAGRAPH", "HEADING", "PARAGRAPH", "HEADING");
        assertThat(store.chunks).allSatisfy(chunk -> {
            assertThat(chunk.getId()).startsWith("20:30:");
            assertThat(chunk.getWorkspaceId()).isEqualTo(20L);
            assertThat(chunk.getOwnerUserId()).isEqualTo(10L);
            assertThat(chunk.getRevisionId()).isEqualTo(100L);
            assertThat(chunk.getEmbedding()).containsExactly(1.0F, 2.0F);
            assertThat(chunk.getCreateTime()).isEqualTo(LocalDateTime.of(2026, 6, 10, 12, 0));
            assertThat(chunk.getIsDeleted()).isFalse();
        });
        assertThat(store.chunks.get(6).getContent())
                .contains("Summary: Refunds are available within seven days.")
                .contains("## Window")
                .contains("## Exceptions");
    }

    @Test
    void skipsStaleRevisionEvents() {
        InMemoryDocumentRepository documents = new InMemoryDocumentRepository();
        InMemoryRevisionRepository revisions = new InMemoryRevisionRepository();
        CapturingStore store = new CapturingStore();

        documents.save(document(10L, 20L, 30L, 101L));
        revisions.save(revision(100L, 30L, BlockDocument.of(List.of(paragraph("p1", "Old")))));

        handler(documents, revisions, store).indexDocumentRevision(30L, 100L);

        assertThat(store.chunks).isEmpty();
    }

    @Test
    void skipsReplaceWhenRevisionBecomesStaleBeforeStoreUpdate() {
        InMemoryDocumentRepository documents = new InMemoryDocumentRepository();
        InMemoryRevisionRepository revisions = new InMemoryRevisionRepository();
        CapturingStore store = new CapturingStore();

        documents.save(document(10L, 20L, 30L, 100L));
        revisions.save(revision(100L, 30L, BlockDocument.of(List.of(paragraph("p1", "Original content")))));

        KnowledgeProperties properties = new KnowledgeProperties();
        properties.setEnabled(true);
        properties.setEmbeddingModelId("embedding");
        KnowledgeIndexCommandHandler handler = new KnowledgeIndexCommandHandler(
                properties,
                documents,
                revisions,
                new KnowledgeChunker(),
                sections -> List.of(),
                new AiEmbeddingRegistry("embedding", List.of(new StubEmbeddingModel() {
                    @Override
                    public EmbeddingResponse embed(EmbeddingRequest request) {
                        documents.findById(30L).ifPresent(document -> document.setCurrentRevisionId(101L));
                        return super.embed(request);
                    }
                })),
                store
        );

        handler.indexDocumentRevision(30L, 100L);

        assertThat(store.chunks).isEmpty();
    }

    @Test
    void splitsEmbeddingRequestsByConfiguredBatchSize() {
        InMemoryDocumentRepository documents = new InMemoryDocumentRepository();
        InMemoryRevisionRepository revisions = new InMemoryRevisionRepository();
        CapturingStore store = new CapturingStore();
        TrackingEmbeddingModel embeddingModel = new TrackingEmbeddingModel();

        documents.save(document(10L, 20L, 30L, 100L));
        revisions.save(revision(100L, 30L, BlockDocument.of(paragraphs(25))));

        KnowledgeProperties properties = new KnowledgeProperties();
        properties.setEnabled(true);
        properties.setEmbeddingModelId("embedding");
        properties.setEmbeddingBatchSize(10);
        KnowledgeIndexCommandHandler handler = new KnowledgeIndexCommandHandler(
                properties,
                documents,
                revisions,
                new KnowledgeChunker(),
                sections -> List.of(),
                new AiEmbeddingRegistry("embedding", List.of(embeddingModel)),
                store
        );

        handler.indexDocumentRevision(30L, 100L);

        assertThat(embeddingModel.batchSizes).containsExactly(10, 10, 5);
        assertThat(store.chunks).hasSize(25);
    }

    @Test
    void indexesSplitSectionSummariesWithDistinctChunkIds() {
        InMemoryDocumentRepository documents = new InMemoryDocumentRepository();
        InMemoryRevisionRepository revisions = new InMemoryRevisionRepository();
        CapturingStore store = new CapturingStore();

        documents.save(document(10L, 20L, 30L, 100L));
        revisions.save(revision(100L, 30L, BlockDocument.of(List.of(
                heading("h1", 1, "Large section"),
                paragraph("p1", "Alpha section fact zero keeps important tail data."),
                paragraph("p2", "Beta section fact one keeps important tail data."),
                paragraph("p3", "Gamma section fact two keeps important tail data."),
                paragraph("p4", "Delta section fact three keeps important tail data."),
                paragraph("p5", "Epsilon section fact four keeps important tail data.")
        ))));

        KnowledgeProperties properties = new KnowledgeProperties();
        properties.setEnabled(true);
        properties.setEmbeddingModelId("embedding");
        KnowledgeIndexCommandHandler handler = new KnowledgeIndexCommandHandler(
                properties,
                documents,
                revisions,
                new KnowledgeChunker(70),
                sections -> sections.stream()
                        .map(this::summary)
                        .toList(),
                new AiEmbeddingRegistry("embedding", List.of(new StubEmbeddingModel())),
                store
        );

        handler.indexDocumentRevision(30L, 100L);

        List<KnowledgeIndexedChunk> summaries = store.chunks.stream()
                .filter(chunk -> KnowledgeChunkType.SECTION_SUMMARY.name().equals(chunk.getChunkType()))
                .toList();
        assertThat(summaries).hasSize(5);
        assertThat(summaries)
                .extracting(KnowledgeIndexedChunk::getChunkIndex)
                .containsExactly(0, 1, 2, 3, 4);
        assertThat(summaries)
                .extracting(KnowledgeIndexedChunk::getId)
                .containsExactly(
                        "20:30:SECTION_SUMMARY:h1:0",
                        "20:30:SECTION_SUMMARY:h1:1",
                        "20:30:SECTION_SUMMARY:h1:2",
                        "20:30:SECTION_SUMMARY:h1:3",
                        "20:30:SECTION_SUMMARY:h1:4"
                );
    }

    private KnowledgeIndexCommandHandler handler(InMemoryDocumentRepository documents,
                                                 InMemoryRevisionRepository revisions,
                                                 CapturingStore store) {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.setEnabled(true);
        properties.setEmbeddingModelId("embedding");
        properties.setSummaryModelId("summary");
        return new KnowledgeIndexCommandHandler(
                properties,
                documents,
                revisions,
                new KnowledgeChunker(),
                sections -> sections.stream()
                        .map(this::summary)
                        .toList(),
                new AiEmbeddingRegistry("embedding", List.of(new StubEmbeddingModel())),
                store
        );
    }

    private KnowledgeChunkDraft summary(KnowledgeSectionDraft section) {
        return KnowledgeChunkDraft.sectionSummary(
                section.getHeadingBlockId(),
                section.getHeadingPath(),
                section.getChunkIndex() == null ? 0 : section.getChunkIndex(),
                "Summary: " + section.getContent()
        );
    }

    private WorkspaceDocument document(Long ownerUserId, Long workspaceId, Long documentId, Long revisionId) {
        WorkspaceDocument document = new WorkspaceDocument();
        document.setId(documentId);
        document.setOwnerUserId(ownerUserId);
        document.setOriginWorkspaceId(workspaceId);
        document.setTitle("Policy");
        document.setCurrentRevisionId(revisionId);
        document.setIsDeleted(false);
        return document;
    }

    private DocumentRevision revision(Long revisionId, Long documentId, BlockDocument snapshot) {
        DocumentRevision revision = new DocumentRevision();
        revision.setId(revisionId);
        revision.setDocumentId(documentId);
        revision.setSnapshot(snapshot);
        revision.setAuthorUserId(10L);
        revision.setCreateTime(LocalDateTime.of(2026, 6, 10, 12, 0));
        revision.setUpdateTime(LocalDateTime.of(2026, 6, 10, 12, 0));
        revision.setCreatorId(10L);
        revision.setUpdaterId(10L);
        revision.setCreateBy("10");
        revision.setUpdateBy("10");
        revision.setIsDeleted(false);
        return revision;
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

    private List<BlockNode> paragraphs(int count) {
        List<BlockNode> nodes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            nodes.add(paragraph("p" + i, "Content " + i));
        }
        return nodes;
    }

    private static class StubEmbeddingModel implements AiEmbeddingModel {

        @Override
        public String id() {
            return "embedding";
        }

        @Override
        public AiModelMetadata metadata() {
            return AiModelMetadata.of(id());
        }

        @Override
        public EmbeddingResponse embed(EmbeddingRequest request) {
            EmbeddingResponse response = new EmbeddingResponse();
            for (int i = 0; i < request.getInputs().size(); i++) {
                response.getEmbeddings().add(new float[]{1.0F, 2.0F});
            }
            return response;
        }
    }

    private static class TrackingEmbeddingModel implements AiEmbeddingModel {

        private final List<Integer> batchSizes = new ArrayList<>();

        @Override
        public String id() {
            return "embedding";
        }

        @Override
        public AiModelMetadata metadata() {
            return AiModelMetadata.of(id());
        }

        @Override
        public EmbeddingResponse embed(EmbeddingRequest request) {
            batchSizes.add(request.getInputs().size());
            EmbeddingResponse response = new EmbeddingResponse();
            for (int i = 0; i < request.getInputs().size(); i++) {
                response.getEmbeddings().add(new float[]{1.0F, 2.0F});
            }
            return response;
        }

    }

    private static class CapturingStore implements KnowledgeChunkStore {

        private Long workspaceId;
        private Long documentId;
        private List<KnowledgeIndexedChunk> chunks = List.of();

        @Override
        public void replaceDocumentChunks(Long workspaceId, Long documentId, List<KnowledgeIndexedChunk> chunks) {
            this.workspaceId = workspaceId;
            this.documentId = documentId;
            this.chunks = new ArrayList<>(chunks);
        }

        @Override
        public void deleteDocumentChunks(Long workspaceId, Long documentId) {
        }

        @Override
        public List<KnowledgeIndexedChunk> search(KnowledgeSearchQuery query) {
            return List.of();
        }
    }

    private static class InMemoryDocumentRepository implements WorkspaceDocumentRepository {

        private final Map<Long, WorkspaceDocument> documents = new LinkedHashMap<>();

        @Override
        public WorkspaceDocument save(WorkspaceDocument document) {
            documents.put(document.getId(), document);
            return document;
        }

        @Override
        public Optional<WorkspaceDocument> findById(Long documentId) {
            return Optional.ofNullable(documents.get(documentId));
        }
    }

    private static class InMemoryRevisionRepository implements DocumentRevisionRepository {

        private final Map<Long, DocumentRevision> revisions = new LinkedHashMap<>();

        @Override
        public DocumentRevision save(DocumentRevision revision) {
            revisions.put(revision.getId(), revision);
            return revision;
        }

        @Override
        public Optional<DocumentRevision> findById(Long revisionId) {
            return Optional.ofNullable(revisions.get(revisionId));
        }

        @Override
        public Optional<DocumentRevision> findByDocumentIdAndClientMutationId(Long documentId, String clientMutationId) {
            return Optional.empty();
        }

        @Override
        public List<DocumentRevision> findByDocumentIdOrderByVersionDesc(Long documentId, int limit) {
            return revisions.values().stream()
                    .filter(revision -> documentId.equals(revision.getDocumentId()))
                    .sorted(Comparator.comparing(DocumentRevision::getVersion).reversed())
                    .limit(limit)
                    .toList();
        }
    }

}
