package io.docpilot.infrastructure.memory;

import io.docpilot.document.model.WorkspaceNode;
import io.docpilot.document.model.WorkspaceNodeState;
import io.docpilot.document.repository.WorkspaceNodeRepository;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
public class InMemoryWorkspaceNodeRepository implements WorkspaceNodeRepository {

    private final ConcurrentMap<String, WorkspaceNode> nodes = new ConcurrentHashMap<>();

    @Override
    public WorkspaceNode save(WorkspaceNode node) {
        nodes.put(node.getNodeId(), node);
        return node;
    }

    @Override
    public Optional<WorkspaceNode> findById(String nodeId) {
        return Optional.ofNullable(nodes.get(nodeId));
    }

    @Override
    public List<WorkspaceNode> findByWorkspaceIdAndParentNodeId(String workspaceId, String parentNodeId) {
        return nodes.values().stream()
                .filter(node -> workspaceId.equals(node.getWorkspaceId()))
                .filter(node -> parentNodeId.equals(node.getParentNodeId()))
                .filter(node -> node.getState() == WorkspaceNodeState.ACTIVE)
                .sorted(Comparator.comparingLong(WorkspaceNode::getSortOrder)
                        .thenComparing(WorkspaceNode::getCreateTime))
                .toList();
    }

}
