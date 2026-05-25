package io.docpilot.document.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Node in a workspace resource tree.
 */
@Getter
@Setter
public class WorkspaceNode {

    /**
     * Node id used by APIs and storage.
     */
    private String nodeId;

    /**
     * Workspace that owns this node.
     */
    private String workspaceId;

    /**
     * Parent node id. Null means the implicit workspace root.
     */
    private String parentNodeId;

    /**
     * Node type, currently folder or document.
     */
    private WorkspaceNodeType type;

    /**
     * User-facing node name.
     */
    private String name;

    /**
     * Linked document id when this node is a document node.
     */
    private String documentId;

    /**
     * Sort value for later tree ordering.
     */
    private long sortOrder;

    /**
     * Node lifecycle state.
     */
    private WorkspaceNodeState state = WorkspaceNodeState.ACTIVE;

    /**
     * Creation time.
     */
    private Instant createTime;

    /**
     * Last update time.
     */
    private Instant updateTime;

    /**
     * Extension metadata for future tree features.
     */
    private Map<String, Object> metadata = new HashMap<>();

}
