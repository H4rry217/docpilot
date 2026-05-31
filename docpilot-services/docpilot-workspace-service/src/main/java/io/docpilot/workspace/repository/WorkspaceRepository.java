package io.docpilot.workspace.repository;

import io.docpilot.workspace.model.entity.Workspace;

import java.util.List;
import java.util.Optional;

public interface WorkspaceRepository {

    Workspace save(Workspace workspace);

    Optional<Workspace> findById(Long workspaceId);

    Optional<Workspace> findActivePersonalByOwnerUserId(Long ownerUserId);

    List<Workspace> findActiveByOwnerUserId(Long ownerUserId);

}

