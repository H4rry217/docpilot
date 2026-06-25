package io.docpilot.workspace.event;

/**
 * Published after a document, its first revision, and its workspace node are persisted.
 */
public record DocumentCreatedEvent(
        /**
         * User id that owns the workspace containing the new document.
         */
        Long ownerUserId,

        /**
         * Workspace id where the document was created.
         */
        Long workspaceId,

        /**
         * Workspace node id that points to the document resource.
         */
        Long nodeId,

        /**
         * Newly created document id.
         */
        Long documentId,

        /**
         * Initial revision id created with the document.
         */
        Long revisionId,

        /**
         * Initial document version.
         */
        Long version,

        /**
         * Initial display title of the document.
         */
        String title
) {
}
