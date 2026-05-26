package io.docpilot.filesystem;

import io.docpilot.filesystem.exception.FileNotFoundException;
import io.docpilot.filesystem.exception.ReadonlyPathException;
import io.docpilot.filesystem.model.FileEntry;
import io.docpilot.filesystem.model.GrepMatch;
import io.docpilot.filesystem.model.PathMapping;
import io.docpilot.filesystem.model.PathMappingResolution;
import io.docpilot.filesystem.path.FilesystemPath;
import io.docpilot.filesystem.path.FilesystemPathNames;
import io.docpilot.filesystem.provider.FilesystemProvider;
import io.docpilot.filesystem.provider.ProviderRegistry;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

public class FilesystemService {

    private final PathMappingService pathMappingService;
    private final ProviderRegistry providerRegistry;

    public FilesystemService(PathMappingService pathMappingService, ProviderRegistry providerRegistry) {
        this.pathMappingService = pathMappingService;
        this.providerRegistry = providerRegistry;
    }

    public List<FileEntry> list(String workspaceId, String path) {
        ResolvedProvider resolved = resolve(workspaceId, path);
        return resolved.provider().list(resolved.resolution().providerPath()).stream()
                .map(entry -> toVirtualEntry(resolved.resolution().mapping(), entry))
                .toList();
    }

    public byte[] read(String workspaceId, String path) {
        ResolvedProvider resolved = resolve(workspaceId, path);
        return resolved.provider().read(resolved.resolution().providerPath());
    }

    public String readText(String workspaceId, String path) {
        return new String(read(workspaceId, path), StandardCharsets.UTF_8);
    }

    public void write(String workspaceId, String path, byte[] content) {
        ResolvedProvider resolved = resolve(workspaceId, path);
        requireWritable(resolved.resolution().mapping());
        resolved.provider().write(resolved.resolution().providerPath(), content);
    }

    public void writeText(String workspaceId, String path, String content) {
        write(workspaceId, path, content.getBytes(StandardCharsets.UTF_8));
    }

    public void delete(String workspaceId, String path) {
        ResolvedProvider resolved = resolve(workspaceId, path);
        requireWritable(resolved.resolution().mapping());
        resolved.provider().delete(resolved.resolution().providerPath());
    }

    public void copy(String workspaceId, String sourcePath, String targetPath) {
        ResolvedProvider source = resolve(workspaceId, sourcePath);
        ResolvedProvider target = resolve(workspaceId, targetPath);
        requireWritable(target.resolution().mapping());
        if (source.provider().providerId().equals(target.provider().providerId())) {
            source.provider().copy(source.resolution().providerPath(), target.resolution().providerPath());
            return;
        }
        target.provider().write(target.resolution().providerPath(), source.provider().read(source.resolution().providerPath()));
    }

    public void move(String workspaceId, String sourcePath, String targetPath) {
        ResolvedProvider source = resolve(workspaceId, sourcePath);
        ResolvedProvider target = resolve(workspaceId, targetPath);
        requireWritable(source.resolution().mapping());
        requireWritable(target.resolution().mapping());
        if (source.provider().providerId().equals(target.provider().providerId())) {
            source.provider().move(source.resolution().providerPath(), target.resolution().providerPath());
            return;
        }
        // Different providers cannot share metadata, so a move becomes copy first, then delete source.
        target.provider().write(target.resolution().providerPath(), source.provider().read(source.resolution().providerPath()));
        source.provider().delete(source.resolution().providerPath());
    }

    public boolean exists(String workspaceId, String path) {
        try {
            ResolvedProvider resolved = resolve(workspaceId, path);
            return resolved.provider().exists(resolved.resolution().providerPath());
        } catch (FileNotFoundException exception) {
            return false;
        }
    }

    public FileEntry stat(String workspaceId, String path) {
        ResolvedProvider resolved = resolve(workspaceId, path);
        return toVirtualEntry(resolved.resolution().mapping(), resolved.provider().stat(resolved.resolution().providerPath()));
    }

    public List<FileEntry> glob(String workspaceId, String pattern) {
        String normalizedPattern = FilesystemPath.normalizeGlobPattern(pattern);
        // Resolve using the non-wildcard prefix so the whole glob stays inside one PathMapping.
        String staticPrefix = FilesystemPath.staticPrefixForGlob(normalizedPattern);
        ResolvedProvider resolved = resolve(workspaceId, staticPrefix);
        String relativePattern = toRelativePattern(resolved.resolution().mapping().getVirtualPath(), normalizedPattern);
        String providerPattern = FilesystemPath.joinProviderPath(
                resolved.resolution().mapping().getProviderRoot(),
                relativePattern
        );
        return resolved.provider().glob(providerPattern).stream()
                .map(entry -> toVirtualEntry(resolved.resolution().mapping(), entry))
                .toList();
    }

    public List<GrepMatch> grep(String workspaceId, String path, String text) {
        ResolvedProvider resolved = resolve(workspaceId, path);
        return resolved.provider().grep(resolved.resolution().providerPath(), text).stream()
                .map(match -> new GrepMatch(toVirtualPath(resolved.resolution().mapping(), match.path()), match.lineNumber(), match.line()))
                .toList();
    }

    public Optional<String> readUrl(String workspaceId, String path) {
        ResolvedProvider resolved = resolve(workspaceId, path);
        return resolved.provider().readUrl(resolved.resolution().providerPath());
    }

    private ResolvedProvider resolve(String workspaceId, String path) {
        PathMappingResolution resolution = pathMappingService.resolve(workspaceId, path);
        FilesystemProvider provider = providerRegistry.requireById(resolution.mapping().getProviderId());
        return new ResolvedProvider(resolution, provider);
    }

    private void requireWritable(PathMapping mapping) {
        if (mapping.isReadonly()) {
            throw new ReadonlyPathException("Path mapping is readonly: " + mapping.getVirtualPath());
        }
    }

    private FileEntry toVirtualEntry(PathMapping mapping, FileEntry entry) {
        return new FileEntry(
                toVirtualPath(mapping, entry.path()),
                entry.name(),
                entry.type(),
                entry.size(),
                entry.lastModified()
        );
    }

    private String toVirtualPath(PathMapping mapping, String providerPath) {
        String normalizedProviderPath = FilesystemPath.normalizeProviderPath(providerPath);
        String providerRoot = FilesystemPath.normalizeProviderPath(mapping.getProviderRoot());
        String suffix;
        // Providers return provider-local paths; strip the mapped root before returning workspace paths.
        if (StringUtils.isEmpty(providerRoot)) {
            suffix = normalizedProviderPath;
        } else if (normalizedProviderPath.equals(providerRoot)) {
            suffix = "";
        } else if (normalizedProviderPath.startsWith(providerRoot + FilesystemPathNames.ROOT)) {
            suffix = normalizedProviderPath.substring(providerRoot.length() + 1);
        } else {
            suffix = normalizedProviderPath;
        }
        if (StringUtils.isEmpty(suffix)) {
            return mapping.getVirtualPath();
        }
        if (FilesystemPathNames.ROOT.equals(mapping.getVirtualPath())) {
            return FilesystemPathNames.ROOT + suffix;
        }
        return mapping.getVirtualPath() + FilesystemPathNames.ROOT + suffix;
    }

    private String toRelativePattern(String mappingVirtualPath, String normalizedPattern) {
        if (FilesystemPathNames.ROOT.equals(mappingVirtualPath)) {
            return normalizedPattern.substring(1);
        }
        if (normalizedPattern.equals(mappingVirtualPath)) {
            return "";
        }
        return normalizedPattern.substring(mappingVirtualPath.length() + 1);
    }

    private record ResolvedProvider(PathMappingResolution resolution, FilesystemProvider provider) {
    }

}
