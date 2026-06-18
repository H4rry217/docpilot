package io.docpilot.workspace.model.response;

import io.docpilot.workspace.enums.WorkspaceType;

import java.time.LocalDateTime;
import java.util.Map;

public record WorkspaceResponse(
        String workspaceId,
        String name,
        WorkspaceType type,
        String ownerUserId,
        String rootNodeId,
        Map<String, Object> settings,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}
