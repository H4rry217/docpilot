package io.docpilot.workspace.knowledge.queue;

/**
 * Document-level worker lease for the Redis knowledge index queue.
 */
public interface KnowledgeIndexJobLock {

    /**
     * Attempts to claim processing rights for one document.
     *
     * @param documentId document id from the due queue.
     * @param leaseTimeMs maximum lock lifetime in milliseconds.
     * @return true when the caller may process the job.
     */
    boolean tryLock(Long documentId, long leaseTimeMs);

    /**
     * Releases processing rights for one document when held by the caller.
     *
     * @param documentId document id from the due queue.
     */
    void unlock(Long documentId);

}
