package io.docpilot.workspace.event;

/**
 * Published after a non-default workspace is soft deleted.
 */
public record WorkspaceDeletedEvent(
        /**
         * User id that owns the deleted workspace.
         */
        Long ownerUserId,

        /**
         * Soft-deleted workspace id.
         */
        Long workspaceId,

        /**
         * Root folder node id of the deleted workspace.
         */
        Long rootNodeId
) {
}
