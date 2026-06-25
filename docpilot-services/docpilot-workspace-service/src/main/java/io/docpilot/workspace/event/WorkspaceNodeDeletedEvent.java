package io.docpilot.workspace.event;

import java.util.List;

/**
 * Published after one workspace node operation soft deletes nodes.
 */
public record WorkspaceNodeDeletedEvent(
        /**
         * User id that owns the workspace containing the deleted nodes.
         */
        Long ownerUserId,

        /**
         * Workspace id where the delete operation happened.
         */
        Long workspaceId,

        /**
         * Node ids soft-deleted by one delete operation, including descendants.
         */
        List<Long> nodeIds,

        /**
         * Document ids whose resource nodes were deleted by the same operation.
         */
        List<Long> documentIds
) {

    public WorkspaceNodeDeletedEvent {
        nodeIds = nodeIds == null ? List.of() : List.copyOf(nodeIds);
        documentIds = documentIds == null ? List.of() : List.copyOf(documentIds);
    }

}
