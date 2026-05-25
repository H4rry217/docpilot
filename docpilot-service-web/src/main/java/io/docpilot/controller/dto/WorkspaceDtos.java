package io.docpilot.controller.dto;

import io.docpilot.document.model.Workspace;
import io.docpilot.document.model.WorkspaceNode;
import io.docpilot.document.model.WorkspaceNodeType;

import java.util.List;

public final class WorkspaceDtos {

    private WorkspaceDtos() {
    }

    public record CreateWorkspaceRequest(String name) {
    }

    public record ListWorkspaceNodesRequest(String workspaceId, String parentNodeId) {
    }

    public record CreateWorkspaceNodeRequest(
            String workspaceId,
            String parentNodeId,
            WorkspaceNodeType type,
            String name,
            String documentId
    ) {
    }

    public record WorkspaceListResponse(List<Workspace> workspaces) {
    }

    public record WorkspaceNodeListResponse(List<WorkspaceNode> nodes) {
    }

}
