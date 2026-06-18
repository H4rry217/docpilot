package io.docpilot.workspace.model.response;

import io.docpilot.block.model.BlockDocument;

import java.time.LocalDateTime;

public record DocumentRevisionResponse(
        String revisionId,
        String documentId,
        String version,
        String baseVersion,
        String authorUserId,
        BlockDocument snapshot,
        String markdownSnapshot,
        String checksum,
        LocalDateTime createTime) {
}
