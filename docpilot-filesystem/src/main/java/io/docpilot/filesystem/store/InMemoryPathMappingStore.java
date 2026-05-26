package io.docpilot.filesystem.store;

import io.docpilot.filesystem.model.PathMapping;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryPathMappingStore implements PathMappingStore {

    private final ConcurrentMap<String, PathMapping> mappings = new ConcurrentHashMap<>();

    @Override
    public PathMapping save(PathMapping mapping) {
        mappings.put(mapping.getMappingId(), mapping);
        return mapping;
    }

    @Override
    public Optional<PathMapping> findById(String mappingId) {
        return Optional.ofNullable(mappings.get(mappingId));
    }

    @Override
    public List<PathMapping> findByWorkspaceId(String workspaceId) {
        return mappings.values().stream()
                .filter(mapping -> workspaceId.equals(mapping.getWorkspaceId()))
                .sorted(Comparator.comparing(PathMapping::getVirtualPath))
                .toList();
    }

    @Override
    public void deleteById(String mappingId) {
        mappings.remove(mappingId);
    }

}
