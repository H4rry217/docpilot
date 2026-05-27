package io.docpilot.controller.dto;

import io.docpilot.block.prosemirror.ProseMirrorNode;
import io.docpilot.document.model.DocPilotDocument;
import io.docpilot.document.model.DocumentVisibility;

public final class DocumentDtos {

    public record CreateDocumentRequest(String title, String markdown, DocumentVisibility visibility) {
    }

    public record DocumentIdRequest(String documentId) {
    }

    public record SaveDocumentContentRequest(String documentId, String markdown, Long expectedVersion) {
    }

    public record DocumentResponse(DocPilotDocument document, ProseMirrorNode prosemirror) {
    }

}
