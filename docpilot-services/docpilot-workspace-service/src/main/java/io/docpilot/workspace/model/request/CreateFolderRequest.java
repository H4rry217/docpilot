package io.docpilot.workspace.model.request;

public record CreateFolderRequest(String workspaceId, String parentNodeId, String name) {
}
