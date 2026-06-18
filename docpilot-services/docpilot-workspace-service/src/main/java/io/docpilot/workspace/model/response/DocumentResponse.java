package io.docpilot.workspace.model.response;

import java.time.LocalDateTime;
import java.util.Map;

public record DocumentResponse(
        String documentId,
        String ownerUserId,
        String originWorkspaceId,
        String title,
        String currentVersion,
        String currentRevisionId,
        Map<String, Object> metadata,
        DocumentContentResponse content,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}
