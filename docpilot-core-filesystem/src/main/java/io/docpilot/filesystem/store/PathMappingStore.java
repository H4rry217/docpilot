package io.docpilot.filesystem.store;

import io.docpilot.filesystem.model.PathMapping;

import java.util.List;
import java.util.Optional;

public interface PathMappingStore {

    PathMapping save(PathMapping mapping);

    Optional<PathMapping> findById(String mappingId);

    List<PathMapping> findByWorkspaceId(String workspaceId);

    void deleteById(String mappingId);

}
