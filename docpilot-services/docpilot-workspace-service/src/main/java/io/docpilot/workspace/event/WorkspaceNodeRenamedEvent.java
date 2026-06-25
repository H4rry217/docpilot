package io.docpilot.workspace.event;

/**
 * Published after a workspace tree node is renamed.
 */
public record WorkspaceNodeRenamedEvent(
        /**
         * User id that owns the workspace containing the renamed node.
         */
        Long ownerUserId,

        /**
         * Workspace id where the rename operation happened.
         */
        Long workspaceId,

        /**
         * Renamed node id.
         */
        Long nodeId,

        /**
         * Document id when the renamed node is a document resource, otherwise null.
         */
        Long documentId,

        /**
         * Node display name before the rename.
         */
        String oldName,

        /**
         * Node display name after the rename.
         */
        String newName
) {
}
