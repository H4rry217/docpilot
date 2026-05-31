package io.docpilot.controller;

import io.docpilot.common.result.Result;
import io.docpilot.common.web.auth.RequireAuth;
import io.docpilot.workspace.application.WorkspaceApplicationService;
import io.docpilot.workspace.model.request.CreateFolderCommand;
import io.docpilot.workspace.model.request.CreateFolderRequest;
import io.docpilot.workspace.model.request.CreateWorkspaceCommand;
import io.docpilot.workspace.model.request.CreateWorkspaceRequest;
import io.docpilot.workspace.model.request.DeleteNodeRequest;
import io.docpilot.workspace.model.request.DeleteWorkspaceRequest;
import io.docpilot.workspace.model.request.MoveNodeRequest;
import io.docpilot.workspace.model.request.MoveWorkspaceNodeCommand;
import io.docpilot.workspace.model.request.RenameNodeRequest;
import io.docpilot.workspace.model.request.RenameWorkspaceCommand;
import io.docpilot.workspace.model.request.RenameWorkspaceRequest;
import io.docpilot.workspace.model.request.RenameWorkspaceNodeCommand;
import io.docpilot.workspace.model.request.WorkspaceIdRequest;
import io.docpilot.workspace.model.response.WorkspaceListResponse;
import io.docpilot.workspace.model.response.WorkspaceNodeResponse;
import io.docpilot.workspace.model.response.WorkspaceResponse;
import io.docpilot.workspace.model.response.WorkspaceTreeResponse;
import io.docpilot.workspace.processing.WorkspaceIdCodec;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/workspace")
@RequireAuth
public class WorkspaceController {

    @Resource
    private WorkspaceApplicationService workspaceService;

    @Resource
    private WorkspaceIdCodec idCodec;

    @PostMapping("/default/ensure")
    public Result<WorkspaceResponse> ensureDefaultWorkspace() {
        return Result.success(workspaceService.ensureDefaultWorkspace());
    }

    @PostMapping("/list")
    public Result<WorkspaceListResponse> listWorkspaces() {
        return Result.success(workspaceService.listMyWorkspaces());
    }

    @PostMapping("/create")
    public Result<WorkspaceResponse> createWorkspace(@RequestBody CreateWorkspaceRequest request) {
        CreateWorkspaceCommand command = new CreateWorkspaceCommand();
        command.setName(request.name());
        return Result.success(workspaceService.createWorkspace(command));
    }

    @PostMapping("/rename")
    public Result<WorkspaceResponse> renameWorkspace(@RequestBody RenameWorkspaceRequest request) {
        RenameWorkspaceCommand command = new RenameWorkspaceCommand();
        command.setWorkspaceId(idCodec.parseRequired(request.workspaceId(), "workspaceId"));
        command.setName(request.name());
        return Result.success(workspaceService.renameWorkspace(command));
    }

    @PostMapping("/delete")
    public Result<Void> deleteWorkspace(@RequestBody DeleteWorkspaceRequest request) {
        workspaceService.deleteWorkspace(idCodec.parseRequired(request.workspaceId(), "workspaceId"));
        return Result.success();
    }

    @PostMapping("/tree/get")
    public Result<WorkspaceTreeResponse> getTree(@RequestBody WorkspaceIdRequest request) {
        return Result.success(workspaceService.getTree(idCodec.parseRequired(request.workspaceId(), "workspaceId")));
    }

    @PostMapping("/node/create")
    public Result<WorkspaceNodeResponse> createNode(@RequestBody CreateFolderRequest request) {
        CreateFolderCommand command = new CreateFolderCommand();
        command.setWorkspaceId(idCodec.parseRequired(request.workspaceId(), "workspaceId"));
        command.setParentNodeId(idCodec.parseOptional(request.parentNodeId(), "parentNodeId"));
        command.setName(request.name());
        return Result.success(workspaceService.createFolder(command));
    }

    @PostMapping("/node/rename")
    public Result<WorkspaceNodeResponse> renameNode(@RequestBody RenameNodeRequest request) {
        RenameWorkspaceNodeCommand command = new RenameWorkspaceNodeCommand();
        command.setNodeId(idCodec.parseRequired(request.nodeId(), "nodeId"));
        command.setName(request.name());
        return Result.success(workspaceService.renameNode(command));
    }

    @PostMapping("/node/move")
    public Result<WorkspaceNodeResponse> moveNode(@RequestBody MoveNodeRequest request) {
        MoveWorkspaceNodeCommand command = new MoveWorkspaceNodeCommand();
        command.setNodeId(idCodec.parseRequired(request.nodeId(), "nodeId"));
        command.setParentNodeId(idCodec.parseRequired(request.parentNodeId(), "parentNodeId"));
        return Result.success(workspaceService.moveNode(command));
    }

    @PostMapping("/node/delete")
    public Result<Void> deleteNode(@RequestBody DeleteNodeRequest request) {
        workspaceService.deleteNode(idCodec.parseRequired(request.nodeId(), "nodeId"));
        return Result.success();
    }

}


