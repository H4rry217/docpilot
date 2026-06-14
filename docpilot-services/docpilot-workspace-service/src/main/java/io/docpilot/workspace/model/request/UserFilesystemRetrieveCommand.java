package io.docpilot.workspace.model.request;

import io.docpilot.workspace.filesystem.UserFilesystemFailureMode;
import lombok.Getter;
import lombok.Setter;

/**
 * Application command for retrieving context from the current user's filesystem.
 */
@Getter
@Setter
public class UserFilesystemRetrieveCommand {

    /**
     * User-visible filesystem path, such as /workspace/{workspaceId}/docs/a.md.
     */
    private String path;

    /**
     * Natural language or keyword query used for retrieval.
     */
    private String query;

    /**
     * Maximum number of hits to return after global ranking.
     */
    private Integer topK;

    /**
     * Maximum number of characters returned in each hit snippet.
     */
    private Integer maxCharsPerHit;

    /**
     * Per-workspace retrieval failure policy.
     */
    private UserFilesystemFailureMode failureMode = UserFilesystemFailureMode.BEST_EFFORT;

}
