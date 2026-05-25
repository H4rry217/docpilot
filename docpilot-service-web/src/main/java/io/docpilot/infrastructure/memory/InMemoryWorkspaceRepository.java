package io.docpilot.infrastructure.memory;

import io.docpilot.document.model.Workspace;
import io.docpilot.document.repository.WorkspaceRepository;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
public class InMemoryWorkspaceRepository implements WorkspaceRepository {

    private final ConcurrentMap<String, Workspace> workspaces = new ConcurrentHashMap<>();

    @Override
    public Workspace save(Workspace workspace) {
        workspaces.put(workspace.getWorkspaceId(), workspace);
        return workspace;
    }

    @Override
    public Optional<Workspace> findById(String workspaceId) {
        return Optional.ofNullable(workspaces.get(workspaceId));
    }

    @Override
    public List<Workspace> findByOwnerUserId(String ownerUserId) {
        return workspaces.values().stream()
                .filter(workspace -> ownerUserId.equals(workspace.getOwnerUserId()))
                .sorted(Comparator.comparing(Workspace::getCreateTime))
                .toList();
    }

}
