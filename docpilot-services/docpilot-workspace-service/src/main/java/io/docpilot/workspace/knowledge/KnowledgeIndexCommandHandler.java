package io.docpilot.workspace.knowledge;

import io.docpilot.ai.AiEmbeddingModel;
import io.docpilot.ai.AiEmbeddingRegistry;
import io.docpilot.ai.model.EmbeddingRequest;
import io.docpilot.ai.model.EmbeddingResponse;
import io.docpilot.workspace.knowledge.config.KnowledgeProperties;
import io.docpilot.workspace.knowledge.model.KnowledgeChunkDraft;
import io.docpilot.workspace.knowledge.model.KnowledgeChunkingInput;
import io.docpilot.workspace.knowledge.model.KnowledgeChunkingResult;
import io.docpilot.workspace.knowledge.model.KnowledgeIndexedChunk;
import io.docpilot.workspace.knowledge.store.KnowledgeChunkStore;
import io.docpilot.workspace.model.entity.DocumentRevision;
import io.docpilot.workspace.model.entity.WorkspaceDocument;
import io.docpilot.workspace.repository.DocumentRevisionRepository;
import io.docpilot.workspace.repository.WorkspaceDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Handles document knowledge indexing commands.
 */
@Service
public class KnowledgeIndexCommandHandler {

    /**
     * Logger for knowledge indexing lifecycle events.
     */
    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexCommandHandler.class);

    /**
     * Knowledge indexing switches, model ids, and batching limits.
     */
    private final KnowledgeProperties properties;

    /**
     * Repository for loading the current document aggregate before indexing.
     */
    private final WorkspaceDocumentRepository documentRepository;

    /**
     * Repository for loading immutable revision snapshots.
     */
    private final DocumentRevisionRepository revisionRepository;

    /**
     * Converts block snapshots into source chunks and summary candidates.
     */
    private final KnowledgeChunker chunker;

    /**
     * Generates optional section summary chunks for complex sections.
     */
    private final KnowledgeSummaryService summaryService;

    /**
     * Registry used to resolve the configured embedding model for built-in indexing.
     */
    private final AiEmbeddingRegistry embeddingRegistry;

    /**
     * Built-in index storage port for replacing or deleting chunks.
     */
    private final KnowledgeChunkStore chunkStore;

    public KnowledgeIndexCommandHandler(KnowledgeProperties properties,
                                        WorkspaceDocumentRepository documentRepository,
                                        DocumentRevisionRepository revisionRepository,
                                        KnowledgeChunker chunker,
                                        KnowledgeSummaryService summaryService,
                                        AiEmbeddingRegistry embeddingRegistry,
                                        KnowledgeChunkStore chunkStore) {
        this.properties = properties;
        this.documentRepository = documentRepository;
        this.revisionRepository = revisionRepository;
        this.chunker = chunker;
        this.summaryService = summaryService;
        this.embeddingRegistry = embeddingRegistry;
        this.chunkStore = chunkStore;
    }

    public void indexDocumentRevision(Long documentId, Long revisionId) {
        // Knowledge can be disabled without breaking document write flows.
        if (!properties.isEnabled()) {
            log.info("knowledge index skipped reason=disabled documentId={} revisionId={}", documentId, revisionId);
            return;
        }
        // Invalid events are ignored because the source document write already completed.
        if (documentId == null || revisionId == null) {
            log.warn("knowledge index skipped reason=invalid_event documentId={} revisionId={}", documentId, revisionId);
            return;
        }

        log.info("knowledge index start documentId={} revisionId={} indexName={} embeddingModelId={} store={}",
                documentId,
                revisionId,
                properties.getIndexName(),
                properties.getEmbeddingModelId(),
                chunkStore.getClass().getSimpleName());
        WorkspaceDocument document = documentRepository.findById(documentId).orElse(null);
        // Deleted or missing documents should not leave fresh chunks in the index.
        if (document == null || Boolean.TRUE.equals(document.getIsDeleted())) {
            log.info("knowledge index skipped reason=document_missing_or_deleted documentId={} revisionId={}",
                    documentId, revisionId);
            return;
        }
        // Only the current revision is indexed; stale async events are harmless no-ops.
        if (!Objects.equals(document.getCurrentRevisionId(), revisionId)) {
            log.info("knowledge index skipped reason=stale_event documentId={} eventRevisionId={} currentRevisionId={}",
                    documentId, revisionId, document.getCurrentRevisionId());
            return;
        }

        DocumentRevision revision = revisionRepository.findById(revisionId)
                .orElseThrow(() -> new IllegalStateException("Document revision not found: " + revisionId));
        if (!Objects.equals(revision.getDocumentId(), documentId)) {
            throw new IllegalStateException("Revision " + revisionId + " does not belong to document " + documentId);
        }

        KnowledgeChunkingResult chunkingResult = chunker.chunk(input(document, revision));
        log.info("knowledge chunks generated documentId={} revisionId={} blockChunks={} summaryCandidates={}",
                documentId,
                revisionId,
                chunkingResult.chunks().size(),
                chunkingResult.sections().size());
        List<KnowledgeChunkDraft> drafts = new ArrayList<>(chunkingResult.chunks());
        List<KnowledgeChunkDraft> summaries = summaryService.summarize(chunkingResult.sections());
        drafts.addAll(summaries);
        log.info("knowledge summaries generated documentId={} revisionId={} summaries={} totalDrafts={}",
                documentId, revisionId, summaries.size(), drafts.size());
        List<KnowledgeIndexedChunk> chunks = attachEmbeddings(document, revision, drafts);
        if (!isCurrentRevisionBeforeReplace(documentId, revisionId)) {
            return;
        }
        chunkStore.replaceDocumentChunks(document.getOriginWorkspaceId(), document.getId(), chunks);
        log.info("knowledge index updated workspaceId={} documentId={} revisionId={} chunks={} store={}",
                document.getOriginWorkspaceId(),
                documentId,
                revisionId,
                chunks.size(),
                chunkStore.getClass().getSimpleName());
    }

    public void deleteDocumentChunks(Long workspaceId, Long documentId) {
        // Keep deletion idempotent when knowledge is globally disabled.
        if (!properties.isEnabled()) {
            log.info("knowledge delete skipped reason=disabled workspaceId={} documentId={}", workspaceId, documentId);
            return;
        }
        log.info("knowledge delete start workspaceId={} documentId={} store={}",
                workspaceId, documentId, chunkStore.getClass().getSimpleName());
        chunkStore.deleteDocumentChunks(workspaceId, documentId);
        log.info("knowledge delete done workspaceId={} documentId={} store={}",
                workspaceId, documentId, chunkStore.getClass().getSimpleName());
    }

    private KnowledgeChunkingInput input(WorkspaceDocument document, DocumentRevision revision) {
        KnowledgeChunkingInput input = new KnowledgeChunkingInput();
        input.setWorkspaceId(document.getOriginWorkspaceId());
        input.setOwnerUserId(document.getOwnerUserId());
        input.setDocumentId(document.getId());
        input.setRevisionId(revision.getId());
        input.setTitle(document.getTitle());
        input.setSnapshot(revision.getSnapshot());
        input.setCreateTime(defaultTime(revision.getCreateTime()));
        input.setCreateBy(defaultUserName(revision.getCreateBy(), revision.getAuthorUserId()));
        input.setCreatorId(defaultUserId(revision.getCreatorId(), revision.getAuthorUserId()));
        input.setUpdateTime(defaultTime(revision.getUpdateTime()));
        input.setUpdateBy(defaultUserName(revision.getUpdateBy(), revision.getAuthorUserId()));
        input.setUpdaterId(defaultUserId(revision.getUpdaterId(), revision.getAuthorUserId()));
        return input;
    }

    private List<KnowledgeIndexedChunk> attachEmbeddings(WorkspaceDocument document,
                                                         DocumentRevision revision,
                                                         List<KnowledgeChunkDraft> drafts) {
        if (drafts.isEmpty()) {
            log.info("knowledge embedding skipped reason=no_drafts documentId={} revisionId={}",
                    document.getId(), revision.getId());
            return List.of();
        }

        AiEmbeddingModel embeddingModel = embeddingRegistry.resolve(properties.getEmbeddingModelId());
        log.info("knowledge embedding start documentId={} revisionId={} modelId={} drafts={}",
                document.getId(), revision.getId(), embeddingModel.id(), drafts.size());
        List<float[]> embeddings = embed(embeddingModel, drafts);
        // A mismatch would corrupt chunk-to-vector alignment, so fail before replacing index contents.
        if (embeddings.size() != drafts.size()) {
            throw new IllegalStateException("Embedding count does not match chunk count");
        }
        log.info("knowledge embedding done documentId={} revisionId={} modelId={} embeddings={}",
                document.getId(), revision.getId(), embeddingModel.id(), embeddings.size());

        KnowledgeChunkingInput input = input(document, revision);
        List<KnowledgeIndexedChunk> chunks = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i++) {
            KnowledgeChunkDraft draft = drafts.get(i);
            KnowledgeIndexedChunk chunk = new KnowledgeIndexedChunk();
            chunk.setWorkspaceId(input.getWorkspaceId());
            chunk.setOwnerUserId(input.getOwnerUserId());
            chunk.setDocumentId(input.getDocumentId());
            chunk.setRevisionId(input.getRevisionId());
            chunk.setTitle(input.getTitle());
            chunk.setChunkType(draft.getChunkType().name());
            chunk.setBlockId(draft.getBlockId());
            chunk.setBlockType(draft.getBlockType());
            chunk.setChunkIndex(draft.getChunkIndex());
            chunk.setHeadingPath(new ArrayList<>(draft.getHeadingPath()));
            chunk.setContent(draft.getContent());
            chunk.setEmbedding(toList(embeddings.get(i)));
            chunk.setCreateTime(input.getCreateTime());
            chunk.setCreateBy(input.getCreateBy());
            chunk.setCreatorId(input.getCreatorId());
            chunk.setUpdateTime(input.getUpdateTime());
            chunk.setUpdateBy(input.getUpdateBy());
            chunk.setUpdaterId(input.getUpdaterId());
            chunk.setIsDeleted(false);
            chunk.setId(chunkId(chunk));
            chunks.add(chunk);
        }
        return chunks;
    }

    private boolean isCurrentRevisionBeforeReplace(Long documentId, Long revisionId) {
        WorkspaceDocument current = documentRepository.findById(documentId).orElse(null);
        if (current == null || Boolean.TRUE.equals(current.getIsDeleted())) {
            log.info("knowledge index skipped reason=document_missing_or_deleted_before_replace documentId={} revisionId={}",
                    documentId, revisionId);
            return false;
        }
        if (!Objects.equals(current.getCurrentRevisionId(), revisionId)) {
            log.info("knowledge index skipped reason=stale_event stage=before_replace documentId={} eventRevisionId={} currentRevisionId={}",
                    documentId, revisionId, current.getCurrentRevisionId());
            return false;
        }
        return true;
    }

    private List<float[]> embed(AiEmbeddingModel embeddingModel, List<KnowledgeChunkDraft> drafts) {
        List<float[]> embeddings = new ArrayList<>();
        int batchSize = Math.max(1, properties.getEmbeddingBatchSize());
        for (int start = 0; start < drafts.size(); start += batchSize) {
            int end = Math.min(drafts.size(), start + batchSize);
            EmbeddingRequest request = new EmbeddingRequest();
            request.setInputs(drafts.subList(start, end).stream()
                    .map(KnowledgeChunkDraft::getContent)
                    .toList());
            EmbeddingResponse response = embeddingModel.embed(request);
            if (response.getEmbeddings() != null) {
                embeddings.addAll(response.getEmbeddings());
                log.info("knowledge embedding batch done modelId={} batchStart={} batchEnd={} embeddings={}",
                        embeddingModel.id(), start, end, response.getEmbeddings().size());
            } else {
                log.warn("knowledge embedding batch returned null embeddings modelId={} batchStart={} batchEnd={}",
                        embeddingModel.id(), start, end);
            }
        }
        return embeddings;
    }

    private String chunkId(KnowledgeIndexedChunk chunk) {
        return chunk.getWorkspaceId()
                + ":" + chunk.getDocumentId()
                + ":" + chunk.getChunkType()
                + ":" + nullSafe(chunk.getBlockId())
                + ":" + chunk.getChunkIndex();
    }

    private List<Float> toList(float[] values) {
        if (values == null) {
            return null;
        }
        List<Float> result = new ArrayList<>(values.length);
        for (float value : values) {
            result.add(value);
        }
        return result;
    }

    private String nullSafe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private LocalDateTime defaultTime(LocalDateTime value) {
        return value == null ? LocalDateTime.now() : value;
    }

    private Long defaultUserId(Long value, Long fallback) {
        return value == null ? fallback : value;
    }

    private String defaultUserName(String value, Long fallback) {
        if (value != null && !value.isBlank()) {
            return value;
        }
        return fallback == null ? null : String.valueOf(fallback);
    }

}
