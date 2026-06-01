package io.docpilot.filesystem;

import io.docpilot.filesystem.model.FileEntry;
import io.docpilot.filesystem.model.GrepMatch;
import io.docpilot.filesystem.path.FilesystemPath;
import io.docpilot.filesystem.provider.FilesystemProvider;

import java.util.List;
import java.util.Optional;

/**
 * Adapter that lets existing provider implementations participate in the new path-first model.
 */
public class ProviderFilesystem implements Filesystem {

    private final FilesystemProvider provider;

    public ProviderFilesystem(FilesystemProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("provider is required");
        }
        this.provider = provider;
    }

    public FilesystemProvider provider() {
        return provider;
    }

    @Override
    public List<FileEntry> list(String path) {
        return provider.list(toProviderPath(path)).stream()
                .map(this::toVirtualEntry)
                .toList();
    }

    @Override
    public byte[] read(String path) {
        return provider.read(toProviderPath(path));
    }

    @Override
    public void write(String path, byte[] content) {
        provider.write(toProviderPath(path), content);
    }

    @Override
    public void delete(String path) {
        provider.delete(toProviderPath(path));
    }

    @Override
    public boolean exists(String path) {
        return provider.exists(toProviderPath(path));
    }

    @Override
    public FileEntry stat(String path) {
        return toVirtualEntry(provider.stat(toProviderPath(path)));
    }

    @Override
    public void copy(String sourcePath, String targetPath) {
        provider.copy(toProviderPath(sourcePath), toProviderPath(targetPath));
    }

    @Override
    public void move(String sourcePath, String targetPath) {
        provider.move(toProviderPath(sourcePath), toProviderPath(targetPath));
    }

    @Override
    public List<FileEntry> glob(String pathPattern) {
        String normalizedPattern = FilesystemPath.normalizeGlobPattern(pathPattern);

        return provider.glob(FilesystemPath.normalizeProviderPath(normalizedPattern)).stream()
                .map(this::toVirtualEntry)
                .toList();
    }

    @Override
    public List<GrepMatch> grep(String path, String text) {
        return provider.grep(toProviderPath(path), text).stream()
                .map(match -> new GrepMatch(toVirtualPath(match.path()), match.lineNumber(), match.line()))
                .toList();
    }

    @Override
    public Optional<String> readUrl(String path) {
        return provider.readUrl(toProviderPath(path));
    }

    private String toProviderPath(String path) {
        return FilesystemPath.normalizeProviderPath(FilesystemPath.normalizeVirtualPath(path));
    }

    private FileEntry toVirtualEntry(FileEntry entry) {
        return new FileEntry(
                toVirtualPath(entry.path()),
                entry.name(),
                entry.type(),
                entry.size(),
                entry.lastModified()
        );
    }

    private String toVirtualPath(String providerPath) {
        return FilesystemPath.joinVirtualPath("/", providerPath);
    }

}
