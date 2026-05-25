package io.docpilot.document.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Mutable command for creating a workspace.
 */
@Getter
@Setter
public class CreateWorkspaceCommand {

    /**
     * User-facing workspace name.
     */
    private String name;

}
