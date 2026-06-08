package io.docpilot.workspace.model.request;

import io.docpilot.block.model.BlockDocument;

/**
 * Request body for saving document content.
 *
 * @param documentId document to save.
 * @param baseVersion client base version used for optimistic locking.
 * @param blockDocument replacement block document snapshot.
 * @param clientMutationId client-generated idempotency token for this save attempt.
 */
public record SaveDocumentContentRequest(
        String documentId,
        String baseVersion,
        BlockDocument blockDocument,
        String clientMutationId) {
}
