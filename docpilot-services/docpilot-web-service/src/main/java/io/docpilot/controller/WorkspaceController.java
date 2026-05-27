package io.docpilot.controller;

import io.docpilot.common.result.Result;
import io.docpilot.common.web.auth.RequireAuth;
import io.docpilot.controller.dto.WorkspaceDtos.CreateWorkspaceNodeRequest;
import io.docpilot.controller.dto.WorkspaceDtos.CreateWorkspaceRequest;
import io.docpilot.controller.dto.WorkspaceDtos.ListWorkspaceNodesRequest;
import io.docpilot.controller.dto.WorkspaceDtos.WorkspaceListResponse;
import io.docpilot.controller.dto.WorkspaceDtos.WorkspaceNodeListResponse;
import io.docpilot.document.application.WorkspaceManager;
import io.docpilot.document.model.CreateWorkspaceCommand;
import io.docpilot.document.model.CreateWorkspaceNodeCommand;
import io.docpilot.document.model.Workspace;
import io.docpilot.document.model.WorkspaceNode;
import io.docpilot.document.model.WorkspaceNodeType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/workspace")
@RequireAuth
public class WorkspaceController {

    private final WorkspaceManager workspaceManager;

    public WorkspaceController(WorkspaceManager workspaceManager) {
        this.workspaceManager = workspaceManager;
    }

    @PostMapping("/list")
    public Result<WorkspaceListResponse> listWorkspaces() {
        return Result.success(new WorkspaceListResponse(workspaceManager.listMyWorkspaces()));
    }

    @PostMapping("/create")
    public Result<Workspace> createWorkspace(@RequestBody CreateWorkspaceRequest request) {
        CreateWorkspaceCommand command = new CreateWorkspaceCommand();
        command.setName(request.name());
        return Result.success(workspaceManager.createWorkspace(command));
    }

    @PostMapping("/node/list")
    public Result<WorkspaceNodeListResponse> listNodes(@RequestBody ListWorkspaceNodesRequest request) {
        return Result.success(new WorkspaceNodeListResponse(workspaceManager.listChildren(request.workspaceId(), request.parentNodeId())));
    }

    @PostMapping("/node/create")
    public Result<WorkspaceNode> createNode(@RequestBody CreateWorkspaceNodeRequest request) {
        CreateWorkspaceNodeCommand command = new CreateWorkspaceNodeCommand();
        command.setWorkspaceId(request.workspaceId());
        command.setParentNodeId(request.parentNodeId());
        command.setType(request.type() == null ? WorkspaceNodeType.DOCUMENT : request.type());
        command.setName(request.name());
        command.setDocumentId(request.documentId());
        return Result.success(workspaceManager.createNode(command));
    }

}
