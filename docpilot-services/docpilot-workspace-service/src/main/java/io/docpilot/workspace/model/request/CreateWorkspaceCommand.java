package io.docpilot.workspace.model.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateWorkspaceCommand {

    /**
     * Display name of the workspace.
     */
    private String name;

}
