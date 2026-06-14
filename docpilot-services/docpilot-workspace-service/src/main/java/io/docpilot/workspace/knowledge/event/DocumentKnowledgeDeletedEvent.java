package io.docpilot.workspace.knowledge.event;

/**
 * Published after a document is deleted from the workspace tree.
 */
public record DocumentKnowledgeDeletedEvent(
        /**
         * Workspace id used to isolate deletion from other tenants.
         */
        Long workspaceId,

        /**
         * Document id whose indexed chunks should be removed.
         */
        Long documentId
) {
}
