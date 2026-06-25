package io.docpilot.workspace.event;

import io.docpilot.workspace.enums.WorkspaceType;

/**
 * Published after a user-created workspace is persisted.
 */
public record WorkspaceCreatedEvent(
        /**
         * User id that owns the new workspace.
         */
        Long ownerUserId,

        /**
         * Newly created workspace id.
         */
        Long workspaceId,

        /**
         * Root folder node id created for the workspace.
         */
        Long rootNodeId,

        /**
         * Workspace type, for example a custom workspace.
         */
        WorkspaceType workspaceType,

        /**
         * Display name assigned to the workspace.
         */
        String name
) {
}
