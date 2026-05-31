package io.docpilot.workspace.model.response;

import io.docpilot.block.model.BlockDocument;

public record DocumentRevisionResponse(
        String revisionId,
        String documentId,
        String version,
        String baseVersion,
        String authorUserId,
        BlockDocument snapshot,
        String markdownSnapshot,
        String checksum,
        String createTime) {
}
