package io.docpilot.document.repository;

import io.docpilot.document.model.Workspace;

import java.util.List;
import java.util.Optional;

/**
 * Storage boundary for workspaces.
 */
public interface WorkspaceRepository {

    /**
     * Saves a workspace aggregate.
     */
    Workspace save(Workspace workspace);

    /**
     * Finds a workspace by id.
     */
    Optional<Workspace> findById(String workspaceId);

    /**
     * Lists workspaces owned by a user.
     */
    List<Workspace> findByOwnerUserId(String ownerUserId);

}
