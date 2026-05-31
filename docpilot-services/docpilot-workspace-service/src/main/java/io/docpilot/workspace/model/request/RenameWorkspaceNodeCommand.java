package io.docpilot.workspace.model.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RenameWorkspaceNodeCommand {

    /**
     * Node to rename.
     */
    private Long nodeId;

    /**
     * New node name.
     */
    private String name;

}


