package io.docpilot.workspace.knowledge.queue;

/**
 * Pending knowledge indexing job metadata.
 */
public record KnowledgeIndexJob(
        Long documentId,
        Long revisionId,
        long firstQueuedAt,
        long updatedAt,
        long attempts
) {
}
