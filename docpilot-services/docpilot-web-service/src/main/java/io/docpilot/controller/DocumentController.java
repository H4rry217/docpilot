package io.docpilot.controller;

import io.docpilot.common.result.Result;
import io.docpilot.common.web.auth.RequireAuth;
import io.docpilot.workspace.application.DocumentApplicationService;
import io.docpilot.workspace.model.request.CreateDocumentCommand;
import io.docpilot.workspace.model.request.CreateDocumentRequest;
import io.docpilot.workspace.model.request.DocumentIdRequest;
import io.docpilot.workspace.model.request.ListDocumentRevisionRequest;
import io.docpilot.workspace.model.request.SaveDocumentContentCommand;
import io.docpilot.workspace.model.request.SaveDocumentContentRequest;
import io.docpilot.workspace.model.response.DocumentDetailResponse;
import io.docpilot.workspace.model.response.DocumentRevisionListResponse;
import io.docpilot.workspace.processing.WorkspaceIdCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for document content and revision operations.
 */
@RestController
@RequestMapping("/document")
@RequireAuth
public class DocumentController {

    /**
     * Document application service.
     */
    @Autowired
    private DocumentApplicationService documentService;

    /**
     * Codec for parsing and formatting public string ids.
     */
    @Autowired
    private WorkspaceIdCodec idCodec;

    /**
     * Creates a document in a workspace tree.
     */
    @PostMapping("/create")
    public Result<DocumentDetailResponse> createDocument(@RequestBody CreateDocumentRequest request) {
        CreateDocumentCommand command = new CreateDocumentCommand();
        command.setWorkspaceId(idCodec.parseRequired(request.workspaceId(), "workspaceId"));
        command.setParentNodeId(idCodec.parseOptional(request.parentNodeId(), "parentNodeId"));
        command.setTitle(request.title());
        command.setNodeName(request.nodeName());
        command.setBlockDocument(request.blockDocument());
        command.setMarkdown(request.markdown());
        return Result.success(documentService.createDocument(command));
    }

    /**
     * Reads one document with editor payloads.
     */
    @PostMapping("/get")
    public Result<DocumentDetailResponse> getDocument(@RequestBody DocumentIdRequest request) {
        return Result.success(documentService.getDocument(idCodec.parseRequired(request.documentId(), "documentId")));
    }

    /**
     * Saves document content using baseVersion optimistic locking and client mutation id idempotency.
     */
    @PostMapping("/content/save")
    public Result<DocumentDetailResponse> saveContent(@RequestBody SaveDocumentContentRequest request) {
        SaveDocumentContentCommand command = new SaveDocumentContentCommand();
        command.setDocumentId(idCodec.parseRequired(request.documentId(), "documentId"));
        command.setBaseVersion(idCodec.parseRequired(request.baseVersion(), "baseVersion"));
        command.setBlockDocument(request.blockDocument());
        command.setClientMutationId(request.clientMutationId());
        return Result.success(documentService.saveContent(command));
    }

    /**
     * Lists recent document revisions.
     */
    @PostMapping("/revision/list")
    public Result<DocumentRevisionListResponse> listRevisions(@RequestBody ListDocumentRevisionRequest request) {
        int limit = request.limit() == null ? 20 : request.limit();
        return Result.success(documentService.listRevisions(idCodec.parseRequired(request.documentId(), "documentId"), limit));
    }

}
