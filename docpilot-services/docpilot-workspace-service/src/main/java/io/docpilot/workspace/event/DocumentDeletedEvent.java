package io.docpilot.workspace.event;

/**
 * Published after a document is soft deleted from a workspace node.
 */
public record DocumentDeletedEvent(
        /**
         * User id that owns the workspace containing the deleted document.
         */
        Long ownerUserId,

        /**
         * Workspace id where the document was deleted.
         */
        Long workspaceId,

        /**
         * Soft-deleted document id.
         */
        Long documentId,

        /**
         * Workspace node id that referenced the document resource.
         */
        Long nodeId
) {
}
