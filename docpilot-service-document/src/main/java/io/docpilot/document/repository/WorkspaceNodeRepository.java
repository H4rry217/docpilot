package io.docpilot.document.repository;

import io.docpilot.document.model.WorkspaceNode;

import java.util.List;
import java.util.Optional;

/**
 * Storage boundary for workspace tree nodes.
 */
public interface WorkspaceNodeRepository {

    /**
     * Saves a workspace node.
     */
    WorkspaceNode save(WorkspaceNode node);

    /**
     * Finds a node by id.
     */
    Optional<WorkspaceNode> findById(String nodeId);

    /**
     * Lists direct child nodes of a parent node.
     */
    List<WorkspaceNode> findByWorkspaceIdAndParentNodeId(String workspaceId, String parentNodeId);

}
