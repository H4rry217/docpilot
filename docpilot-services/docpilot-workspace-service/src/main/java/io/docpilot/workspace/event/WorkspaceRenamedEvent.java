package io.docpilot.workspace.event;

/**
 * Published after a workspace and its root node are renamed.
 */
public record WorkspaceRenamedEvent(
        /**
         * User id that owns the renamed workspace.
         */
        Long ownerUserId,

        /**
         * Renamed workspace id.
         */
        Long workspaceId,

        /**
         * Root folder node id renamed together with the workspace.
         */
        Long rootNodeId,

        /**
         * Workspace display name before the rename.
         */
        String oldName,

        /**
         * Workspace display name after the rename.
         */
        String newName
) {
}
