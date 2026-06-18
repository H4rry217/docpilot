package io.docpilot.workspace.model.response;

import io.docpilot.workspace.enums.WorkspaceNodeType;
import io.docpilot.workspace.enums.WorkspaceResourceType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record WorkspaceNodeResponse(
        String nodeId,
        String workspaceId,
        String parentNodeId,
        List<String> ancestors,
        WorkspaceNodeType nodeType,
        WorkspaceResourceType resourceType,
        String documentId,
        Map<String, Object> storage,
        String name,
        String mimeType,
        String size,
        String checksum,
        Map<String, Object> metadata,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}
