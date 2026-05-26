package io.docpilot.controller;

import io.docpilot.controller.dto.WorkspaceDtos.CreateWorkspaceNodeRequest;
import io.docpilot.controller.dto.WorkspaceDtos.CreateWorkspaceRequest;
import io.docpilot.controller.dto.WorkspaceDtos.ListWorkspaceNodesRequest;
import io.docpilot.controller.dto.WorkspaceDtos.WorkspaceListResponse;
import io.docpilot.controller.dto.WorkspaceDtos.WorkspaceNodeListResponse;
import io.docpilot.document.application.WorkspaceManager;
import io.docpilot.document.model.CreateWorkspaceCommand;
import io.docpilot.document.model.CreateWorkspaceNodeCommand;
import io.docpilot.document.model.WorkspaceNodeType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WorkspaceController {

    private final WorkspaceManager workspaceManager;

    public WorkspaceController(WorkspaceManager workspaceManager) {
        this.workspaceManager = workspaceManager;
    }

    @PostMapping("/workspace/list")
    public WorkspaceListResponse listWorkspaces() {
        return new WorkspaceListResponse(workspaceManager.listMyWorkspaces());
    }

    @PostMapping("/workspace/create")
    public Object createWorkspace(@RequestBody CreateWorkspaceRequest request) {
        CreateWorkspaceCommand command = new CreateWorkspaceCommand();
        command.setName(request.name());
        return workspaceManager.createWorkspace(command);
    }

    @PostMapping("/workspace/node/list")
    public WorkspaceNodeListResponse listNodes(@RequestBody ListWorkspaceNodesRequest request) {
        return new WorkspaceNodeListResponse(workspaceManager.listChildren(request.workspaceId(), request.parentNodeId()));
    }

    @PostMapping("/workspace/node/create")
    public Object createNode(@RequestBody CreateWorkspaceNodeRequest request) {
        CreateWorkspaceNodeCommand command = new CreateWorkspaceNodeCommand();
        command.setWorkspaceId(request.workspaceId());
        command.setParentNodeId(request.parentNodeId());
        command.setType(request.type() == null ? WorkspaceNodeType.DOCUMENT : request.type());
        command.setName(request.name());
        command.setDocumentId(request.documentId());
        return workspaceManager.createNode(command);
    }

}
