package io.docpilot.workspace.repository;

import io.docpilot.workspace.model.entity.WorkspaceNode;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WorkspaceNodeRepository {

    WorkspaceNode save(WorkspaceNode node);

    List<WorkspaceNode> saveAll(Collection<WorkspaceNode> nodes);

    Optional<WorkspaceNode> findById(Long nodeId);

    List<WorkspaceNode> findActiveByWorkspaceId(Long workspaceId);

    Optional<WorkspaceNode> findActiveByWorkspaceIdAndParentNodeIdAndName(
            Long workspaceId,
            Long parentNodeId,
            String name
    );

    List<WorkspaceNode> findActiveByWorkspaceIdAndAncestor(Long workspaceId, Long ancestorNodeId);

}

