package io.docpilot.workspace.knowledge.queue;

/**
 * Queue for document knowledge indexing jobs.
 */
public interface KnowledgeIndexQueue {

    /**
     * Queues the latest known revision for one document.
     *
     * @param documentId document id.
     * @param revisionId revision id to index.
     */
    void enqueueDocumentRevision(Long documentId, Long revisionId);

    /**
     * Cancels pending jobs for one deleted document.
     *
     * @param documentId document id.
     */
    void cancelDocument(Long documentId);

}
