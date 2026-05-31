package io.docpilot.workspace.model.request;

import io.docpilot.block.model.BlockDocument;

public record SaveDocumentContentRequest(
        String documentId,
        String baseVersion,
        BlockDocument blockDocument,
        String clientMutationId) {
}
