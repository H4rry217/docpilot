package io.docpilot.filesystem;

import io.docpilot.filesystem.path.FilesystemPath;
import io.docpilot.filesystem.path.FilesystemPathNames;

public final class FilesystemDefaultPaths {

    public static final String PROJECT_VIRTUAL_PATH = FilesystemPathNames.ROOT + "project";

    private static final String WORKSPACES_ROOT = "workspaces";
    private static final String PROJECT_ROOT_NAME = "project";

    private FilesystemDefaultPaths() {
    }

    public static String workspaceProjectProviderRoot(String workspaceId) {
        return FilesystemPath.joinProviderPath(
                FilesystemPath.joinProviderPath(WORKSPACES_ROOT, workspaceId),
                PROJECT_ROOT_NAME
        );
    }

}
