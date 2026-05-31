package io.docpilot.workspace.model.response;

import java.util.List;

public record WorkspaceTreeResponse(WorkspaceResponse workspace, List<WorkspaceNodeResponse> nodes) {
}
