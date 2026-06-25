package io.docpilot.workspace.event;

import io.docpilot.workspace.enums.WorkspaceNodeType;
import io.docpilot.workspace.enums.WorkspaceResourceType;

/**
 * Published after a workspace tree node is persisted.
 */
public record WorkspaceNodeCreatedEvent(
        /**
         * User id that owns the workspace containing the new node.
         */
        Long ownerUserId,

        /**
         * Workspace id where the node was created.
         */
        Long workspaceId,

        /**
         * Newly created node id.
         */
        Long nodeId,

        /**
         * Parent folder node id, or null when the node is a workspace root.
         */
        Long parentNodeId,

        /**
         * Tree node type, such as folder or resource.
         */
        WorkspaceNodeType nodeType,

        /**
         * Resource type for resource nodes, or null for folder nodes.
         */
        WorkspaceResourceType resourceType,

        /**
         * Document id for document resource nodes, otherwise null.
         */
        Long documentId,

        /**
         * Display name of the created node.
         */
        String name
) {
}
