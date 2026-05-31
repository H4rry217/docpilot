package io.docpilot.workspace.model.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateFolderCommand {

    /**
     * Workspace where the folder will be created.
     */
    private Long workspaceId;

    /**
     * Parent folder node id.
     */
    private Long parentNodeId;

    /**
     * New folder name.
     */
    private String name;

}


