package io.docpilot.workspace.model.request;

public record ListDocumentRevisionRequest(String documentId, Integer limit) {
}
