package io.docpilot.controller;

import io.docpilot.block.processing.ProseMirrorJsonConverter;
import io.docpilot.controller.dto.DocumentDtos.CreateDocumentRequest;
import io.docpilot.controller.dto.DocumentDtos.DocumentIdRequest;
import io.docpilot.controller.dto.DocumentDtos.DocumentResponse;
import io.docpilot.controller.dto.DocumentDtos.SaveDocumentContentRequest;
import io.docpilot.document.application.DocumentManager;
import io.docpilot.document.model.CreateDocumentCommand;
import io.docpilot.document.model.DocPilotDocument;
import io.docpilot.document.model.DocumentVisibility;
import io.docpilot.document.model.UpdateDocumentContentCommand;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DocumentController {

    private final DocumentManager documentManager;
    private final ProseMirrorJsonConverter proseMirrorJsonConverter = new ProseMirrorJsonConverter();

    public DocumentController(DocumentManager documentManager) {
        this.documentManager = documentManager;
    }

    @PostMapping("/document/create")
    public DocumentResponse createDocument(@RequestBody CreateDocumentRequest request) {
        CreateDocumentCommand command = new CreateDocumentCommand();
        command.setTitle(request.title());
        command.setMarkdown(request.markdown());
        command.setVisibility(request.visibility() == null ? DocumentVisibility.PRIVATE : request.visibility());
        return toResponse(documentManager.createDocument(command));
    }

    @PostMapping("/document/get")
    public DocumentResponse getDocument(@RequestBody DocumentIdRequest request) {
        return toResponse(documentManager.getDocument(request.documentId()));
    }

    @PostMapping("/document/content/save")
    public DocumentResponse saveContent(@RequestBody SaveDocumentContentRequest request) {
        UpdateDocumentContentCommand command = new UpdateDocumentContentCommand();
        command.setMarkdown(request.markdown());
        command.setExpectedVersion(request.expectedVersion());
        return toResponse(documentManager.replaceContent(request.documentId(), command));
    }

    private DocumentResponse toResponse(DocPilotDocument document) {
        return new DocumentResponse(document, proseMirrorJsonConverter.toProseMirror(document.getBlockDocument()));
    }

}
