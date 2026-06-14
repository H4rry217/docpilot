package io.docpilot.workspace.knowledge.store;

import io.docpilot.workspace.knowledge.model.KnowledgeIndexedChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Disabled-store implementation used when knowledge indexing is not configured.
 */
public class NoopKnowledgeChunkStore implements KnowledgeChunkStore {

    private static final Logger log = LoggerFactory.getLogger(NoopKnowledgeChunkStore.class);

    @Override
    public void replaceDocumentChunks(Long workspaceId, Long documentId, List<KnowledgeIndexedChunk> chunks) {
        log.warn("knowledge chunks dropped reason=no_chunk_store workspaceId={} documentId={} chunks={}",
                workspaceId, documentId, chunks == null ? 0 : chunks.size());
    }

    @Override
    public void deleteDocumentChunks(Long workspaceId, Long documentId) {
        log.warn("knowledge delete ignored reason=no_chunk_store workspaceId={} documentId={}", workspaceId, documentId);
    }

    @Override
    public List<KnowledgeIndexedChunk> search(KnowledgeSearchQuery query) {
        log.debug("knowledge search ignored reason=no_chunk_store workspaceId={}",
                query == null ? null : query.getWorkspaceId());
        return List.of();
    }

}
