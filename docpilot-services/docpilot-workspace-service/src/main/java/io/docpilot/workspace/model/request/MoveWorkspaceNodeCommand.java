package io.docpilot.workspace.model.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MoveWorkspaceNodeCommand {

    /**
     * Node to move.
     */
    private Long nodeId;

    /**
     * Target parent folder node id.
     */
    private Long parentNodeId;

}


