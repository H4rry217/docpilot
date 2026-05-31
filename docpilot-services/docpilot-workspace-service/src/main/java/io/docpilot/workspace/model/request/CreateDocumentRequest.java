package io.docpilot.workspace.model.request;

import io.docpilot.block.model.BlockDocument;

public record CreateDocumentRequest(
        String workspaceId,
        String parentNodeId,
        String title,
        String nodeName,
        BlockDocument blockDocument,
        String markdown) {
}
