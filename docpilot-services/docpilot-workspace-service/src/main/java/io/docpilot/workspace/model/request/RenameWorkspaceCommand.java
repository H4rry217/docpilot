package io.docpilot.workspace.model.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RenameWorkspaceCommand {

    /**
     * Workspace to rename.
     */
    private Long workspaceId;

    /**
     * New workspace display name.
     */
    private String name;

}
