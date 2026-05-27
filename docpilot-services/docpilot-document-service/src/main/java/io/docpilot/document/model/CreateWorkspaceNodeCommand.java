package io.docpilot.document.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Mutable command for creating a top-level workspace node skeleton.
 */
@Getter
@Setter
public class CreateWorkspaceNodeCommand {

    /**
     * Workspace id.
     */
    private String workspaceId;

    /**
     * Parent node id. Null means the workspace root.
     */
    private String parentNodeId;

    /**
     * Node type.
     */
    private WorkspaceNodeType type;

    /**
     * User-facing node name.
     */
    private String name;

    /**
     * Linked document id when type is DOCUMENT.
     */
    private String documentId;

}
