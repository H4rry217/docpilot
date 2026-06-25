package io.docpilot.workspace.event;

/**
 * Published when a user's personal workspace has just been initialized.
 */
public record DefaultWorkspaceInitializedEvent(
        /**
         * Owner user id for the initialized namespace.
         */
        Long ownerUserId,

        /**
         * Best-effort display name for diagnostics and future event consumers.
         */
        String ownerDisplayName,

        /**
         * Workspace id mounted under the user's /workspace namespace.
         */
        Long workspaceId,

        /**
         * Root folder node id for the initialized workspace.
         */
        Long rootNodeId
) {
}
