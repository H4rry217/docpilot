package io.docpilot.workspace.knowledge.event;

/**
 * Published after a document revision is created and should be indexed.
 */
public record DocumentContentChangedEvent(
        /**
         * Document aggregate id whose latest revision should be indexed.
         */
        Long documentId,

        /**
         * Revision id that provides the immutable indexing snapshot.
         */
        Long revisionId
) {
}
