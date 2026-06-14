package io.docpilot.workspace.knowledge.store;

import io.docpilot.workspace.knowledge.model.KnowledgeIndexedChunk;

import java.util.List;

/**
 * Search-index storage port for knowledge chunks.
 */
public interface KnowledgeChunkStore {

    /**
     * Replaces all built-in index chunks for one document.
     *
     * @param workspaceId workspace id used for tenant isolation.
     * @param documentId document id whose chunks are being replaced.
     * @param chunks fully prepared chunks with embeddings and audit metadata.
     */
    void replaceDocumentChunks(Long workspaceId, Long documentId, List<KnowledgeIndexedChunk> chunks);

    /**
     * Deletes all built-in index chunks for one document.
     *
     * @param workspaceId workspace id used for tenant isolation.
     * @param documentId document id whose chunks should be removed.
     */
    void deleteDocumentChunks(Long workspaceId, Long documentId);

    /**
     * Searches DocPilot-managed chunks with provider-specific ranking.
     *
     * @param query workspace-aware search query containing scope and ranking hints.
     * @return ranked chunks matching the query.
     */
    List<KnowledgeIndexedChunk> search(KnowledgeSearchQuery query);

}
