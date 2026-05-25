package io.docpilot.filesystem;

import io.docpilot.filesystem.exception.FileNotFoundException;
import io.docpilot.filesystem.model.PathMapping;
import io.docpilot.filesystem.model.PathMappingResolution;
import io.docpilot.filesystem.path.FilesystemPath;
import io.docpilot.filesystem.path.FilesystemPathNames;
import io.docpilot.filesystem.store.PathMappingStore;
import org.apache.commons.lang3.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class PathMappingService {

    private final PathMappingStore store;
    private final Clock clock;

    public PathMappingService(PathMappingStore store) {
        this(store, Clock.systemDefaultZone());
    }

    public PathMappingService(PathMappingStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public PathMapping create(PathMapping mapping) {
        validateRequired(mapping);
        Instant now = clock.instant();
        PathMapping normalized = normalize(mapping);
        if (StringUtils.isBlank(normalized.getMappingId())) {
            normalized.setMappingId(StringUtils.remove(UUID.randomUUID().toString(), '-'));
        }
        normalized.setCreateTime(now);
        normalized.setUpdateTime(now);
        ensureUniqueVirtualPath(normalized);
        return store.save(normalized);
    }

    public PathMapping update(PathMapping mapping) {
        if (StringUtils.isBlank(mapping.getMappingId())) {
            throw new IllegalArgumentException("mappingId is required");
        }
        PathMapping existing = store.findById(mapping.getMappingId())
                .orElseThrow(() -> new FileNotFoundException("Path mapping not found: " + mapping.getMappingId()));
        validateRequired(mapping);
        PathMapping normalized = normalize(mapping);
        normalized.setCreateTime(existing.getCreateTime());
        normalized.setUpdateTime(clock.instant());
        ensureUniqueVirtualPath(normalized);
        return store.save(normalized);
    }

    public void delete(String mappingId) {
        store.deleteById(mappingId);
    }

    public List<PathMapping> list(String workspaceId) {
        return store.findByWorkspaceId(workspaceId);
    }

    public PathMappingResolution resolve(String workspaceId, String virtualPath) {
        String normalizedVirtualPath = FilesystemPath.normalizeVirtualPath(virtualPath);
        // Pick the most specific mapping: /project/tmp wins over /project for /project/tmp/a.txt.
        PathMapping mapping = store.findByWorkspaceId(workspaceId).stream()
                .filter(PathMapping::isEnabled)
                .filter(candidate -> matches(candidate.getVirtualPath(), normalizedVirtualPath))
                .max(Comparator.comparingInt(candidate -> candidate.getVirtualPath().length()))
                .orElseThrow(() -> new FileNotFoundException("No path mapping for " + normalizedVirtualPath));
        String relativePath = relativePath(mapping.getVirtualPath(), normalizedVirtualPath);
        String providerPath = FilesystemPath.joinProviderPath(mapping.getProviderRoot(), relativePath);
        return new PathMappingResolution(mapping, normalizedVirtualPath, relativePath, providerPath);
    }

    private PathMapping normalize(PathMapping mapping) {
        PathMapping normalized = new PathMapping();
        normalized.setMappingId(mapping.getMappingId());
        normalized.setWorkspaceId(StringUtils.strip(mapping.getWorkspaceId()));
        // Store canonical paths so duplicate checks and prefix matching do not depend on caller formatting.
        normalized.setVirtualPath(FilesystemPath.normalizeVirtualPath(mapping.getVirtualPath()));
        normalized.setProviderId(StringUtils.strip(mapping.getProviderId()));
        normalized.setProviderRoot(FilesystemPath.normalizeProviderPath(mapping.getProviderRoot()));
        normalized.setReadonly(mapping.isReadonly());
        normalized.setEnabled(mapping.isEnabled());
        normalized.setCreateTime(mapping.getCreateTime());
        normalized.setUpdateTime(mapping.getUpdateTime());
        return normalized;
    }

    private void validateRequired(PathMapping mapping) {
        if (mapping == null) {
            throw new IllegalArgumentException("PathMapping is required");
        }
        if (StringUtils.isBlank(mapping.getWorkspaceId())) {
            throw new IllegalArgumentException("workspaceId is required");
        }
        if (StringUtils.isBlank(mapping.getVirtualPath())) {
            throw new IllegalArgumentException("virtualPath is required");
        }
        if (StringUtils.isBlank(mapping.getProviderId())) {
            throw new IllegalArgumentException("providerId is required");
        }
    }

    private void ensureUniqueVirtualPath(PathMapping mapping) {
        boolean duplicate = store.findByWorkspaceId(mapping.getWorkspaceId()).stream()
                .filter(existing -> !Objects.equals(existing.getMappingId(), mapping.getMappingId()))
                .anyMatch(existing -> Objects.equals(existing.getVirtualPath(), mapping.getVirtualPath()));
        if (duplicate) {
            throw new IllegalArgumentException("Path mapping already exists: " + mapping.getVirtualPath());
        }
    }

    private boolean matches(String mappingPath, String virtualPath) {
        return FilesystemPathNames.ROOT.equals(mappingPath)
                || virtualPath.equals(mappingPath)
                || virtualPath.startsWith(mappingPath + FilesystemPathNames.ROOT);
    }

    private String relativePath(String mappingPath, String virtualPath) {
        if (FilesystemPathNames.ROOT.equals(mappingPath)) {
            return FilesystemPath.normalizeProviderPath(virtualPath);
        }
        if (virtualPath.equals(mappingPath)) {
            return "";
        }
        return FilesystemPath.normalizeProviderPath(virtualPath.substring(mappingPath.length() + 1));
    }

}
