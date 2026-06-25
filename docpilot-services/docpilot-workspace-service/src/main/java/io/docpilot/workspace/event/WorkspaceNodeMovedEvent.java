package io.docpilot.workspace.event;

import java.util.List;

/**
 * Published after a workspace tree node is moved to another parent.
 */
public record WorkspaceNodeMovedEvent(
        /**
         * User id that owns the workspace containing the moved node.
         */
        Long ownerUserId,

        /**
         * Workspace id where the move operation happened.
         */
        Long workspaceId,

        /**
         * Moved node id.
         */
        Long nodeId,

        /**
         * Document id when the moved node is a document resource, otherwise null.
         */
        Long documentId,

        /**
         * Parent folder node id before the move.
         */
        Long oldParentNodeId,

        /**
         * Parent folder node id after the move.
         */
        Long newParentNodeId,

        /**
         * Ancestor node ids before the move, from root to direct parent.
         */
        List<Long> oldAncestors,

        /**
         * Ancestor node ids after the move, from root to direct parent.
         */
        List<Long> newAncestors
) {

    public WorkspaceNodeMovedEvent {
        oldAncestors = oldAncestors == null ? List.of() : List.copyOf(oldAncestors);
        newAncestors = newAncestors == null ? List.of() : List.copyOf(newAncestors);
    }

}
