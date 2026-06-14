package io.docpilot.workspace.knowledge.queue;

import java.util.List;

/**
 * Storage operations used by the debounced knowledge indexing queue.
 */
public interface KnowledgeIndexJobStore {

    /**
     * Saves or updates a debounced job and returns its due timestamp.
     */
    long enqueue(Long documentId,
                 Long revisionId,
                 long nowMillis,
                 long debounceDelayMs,
                 long maxDelayMs,
                 long jobTtlMs);

    /**
     * Lists due document ids without removing them.
     */
    List<Long> dueDocumentIds(long nowMillis, int batchSize);

    /**
     * Reads pending job metadata for one document.
     */
    KnowledgeIndexJob findJob(Long documentId);

    /**
     * Removes the job only if Redis still points at the processed revision.
     */
    boolean completeIfRevisionUnchanged(Long documentId, Long revisionId);

    /**
     * Reschedules the job only if Redis still points at the failed revision.
     */
    long retryIfRevisionUnchanged(Long documentId,
                                  Long revisionId,
                                  long nowMillis,
                                  long retryAtMillis,
                                  long jobTtlMs);

    /**
     * Removes pending queue state for a deleted document.
     */
    void cancel(Long documentId);

}
