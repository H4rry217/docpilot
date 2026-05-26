package io.docpilot.document.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Workspace owned by a user. It is the top-level container for a document tree.
 */
@Getter
@Setter
public class Workspace {

    /**
     * Workspace id used by APIs and storage.
     */
    private String workspaceId;

    /**
     * User id of the workspace owner.
     */
    private String ownerUserId;

    /**
     * User-facing workspace name.
     */
    private String name;

    /**
     * Id of the implicit root node for the workspace tree.
     */
    private String rootNodeId;

    /**
     * Workspace lifecycle state.
     */
    private WorkspaceState state = WorkspaceState.ACTIVE;

    /**
     * Creation time.
     */
    private Instant createTime;

    /**
     * Last update time.
     */
    private Instant updateTime;

    /**
     * Extension metadata for future workspace settings.
     */
    private Map<String, Object> metadata = new HashMap<>();

}
