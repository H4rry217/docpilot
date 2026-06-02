package io.docpilot.filesystem;

import io.docpilot.filesystem.model.FileEntry;
import io.docpilot.filesystem.model.GrepMatch;
import io.docpilot.filesystem.model.GrepOptions;
import io.docpilot.filesystem.model.GrepResult;
import io.docpilot.filesystem.path.FilesystemPath;
import io.docpilot.filesystem.provider.FilesystemProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Adapter that lets existing provider implementations participate in the new path-first model.
 */
public class ProviderFilesystem implements Filesystem {

    private static final Logger log = LoggerFactory.getLogger(ProviderFilesystem.class);

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
        String providerPath = toProviderPath(path);
        log.debug("filesystem provider list start providerId={} path={} providerPath={}",
                provider.providerId(), path, providerPath);
        List<FileEntry> entries = provider.list(providerPath).stream()
                .map(this::toVirtualEntry)
                .toList();
        log.debug("filesystem provider list done providerId={} path={} providerPath={} entries={}",
                provider.providerId(), path, providerPath, entries.size());
        return entries;
    }

    @Override
    public byte[] read(String path) {
        String providerPath = toProviderPath(path);
        log.debug("filesystem provider read start providerId={} path={} providerPath={}",
                provider.providerId(), path, providerPath);
        byte[] content = provider.read(providerPath);
        log.debug("filesystem provider read done providerId={} path={} providerPath={} bytes={}",
                provider.providerId(), path, providerPath, content.length);
        return content;
    }

    @Override
    public void write(String path, byte[] content) {
        String providerPath = toProviderPath(path);
        int bytes = content == null ? 0 : content.length;
        log.debug("filesystem provider write start providerId={} path={} providerPath={} bytes={}",
                provider.providerId(), path, providerPath, bytes);
        provider.write(providerPath, content);
        log.debug("filesystem provider write done providerId={} path={} providerPath={} bytes={}",
                provider.providerId(), path, providerPath, bytes);
    }

    @Override
    public void delete(String path) {
        String providerPath = toProviderPath(path);
        log.debug("filesystem provider delete start providerId={} path={} providerPath={}",
                provider.providerId(), path, providerPath);
        provider.delete(providerPath);
        log.debug("filesystem provider delete done providerId={} path={} providerPath={}",
                provider.providerId(), path, providerPath);
    }

    @Override
    public boolean exists(String path) {
        String providerPath = toProviderPath(path);
        log.debug("filesystem provider exists start providerId={} path={} providerPath={}",
                provider.providerId(), path, providerPath);
        boolean exists = provider.exists(providerPath);
        log.debug("filesystem provider exists done providerId={} path={} providerPath={} exists={}",
                provider.providerId(), path, providerPath, exists);
        return exists;
    }

    @Override
    public FileEntry stat(String path) {
        String providerPath = toProviderPath(path);
        log.debug("filesystem provider stat start providerId={} path={} providerPath={}",
                provider.providerId(), path, providerPath);
        FileEntry entry = toVirtualEntry(provider.stat(providerPath));
        log.debug("filesystem provider stat done providerId={} path={} providerPath={} type={} size={}",
                provider.providerId(), path, providerPath, entry.type(), entry.size());
        return entry;
    }

    @Override
    public void copy(String sourcePath, String targetPath) {
        String providerSourcePath = toProviderPath(sourcePath);
        String providerTargetPath = toProviderPath(targetPath);
        log.debug("filesystem provider copy start providerId={} sourcePath={} targetPath={} providerSourcePath={} providerTargetPath={}",
                provider.providerId(), sourcePath, targetPath, providerSourcePath, providerTargetPath);
        provider.copy(providerSourcePath, providerTargetPath);
        log.debug("filesystem provider copy done providerId={} sourcePath={} targetPath={} providerSourcePath={} providerTargetPath={}",
                provider.providerId(), sourcePath, targetPath, providerSourcePath, providerTargetPath);
    }

    @Override
    public void move(String sourcePath, String targetPath) {
        String providerSourcePath = toProviderPath(sourcePath);
        String providerTargetPath = toProviderPath(targetPath);
        log.debug("filesystem provider move start providerId={} sourcePath={} targetPath={} providerSourcePath={} providerTargetPath={}",
                provider.providerId(), sourcePath, targetPath, providerSourcePath, providerTargetPath);
        provider.move(providerSourcePath, providerTargetPath);
        log.debug("filesystem provider move done providerId={} sourcePath={} targetPath={} providerSourcePath={} providerTargetPath={}",
                provider.providerId(), sourcePath, targetPath, providerSourcePath, providerTargetPath);
    }

    @Override
    public List<FileEntry> glob(String pathPattern) {
        String normalizedPattern = FilesystemPath.normalizeGlobPattern(pathPattern);
        String providerPattern = FilesystemPath.normalizeProviderPath(normalizedPattern);

        log.debug("filesystem provider glob start providerId={} pattern={} providerPattern={}",
                provider.providerId(), pathPattern, providerPattern);
        List<FileEntry> entries = provider.glob(providerPattern).stream()
                .map(this::toVirtualEntry)
                .toList();
        log.debug("filesystem provider glob done providerId={} pattern={} providerPattern={} entries={}",
                provider.providerId(), pathPattern, providerPattern, entries.size());
        return entries;
    }

    @Override
    public List<GrepMatch> grep(String path, String text) {
        return grep(path, text, GrepOptions.unlimited()).matches();
    }

    @Override
    public GrepResult grep(String path, String text, GrepOptions options) {
        String providerPath = toProviderPath(path);
        GrepOptions effectiveOptions = GrepOptions.effective(options);
        log.debug("filesystem provider grep start providerId={} path={} providerPath={} textLength={} maxFiles={} maxMatches={}",
                provider.providerId(), path, providerPath, text == null ? 0 : text.length(),
                effectiveOptions.maxFiles(), effectiveOptions.maxMatches());
        GrepResult result = provider.grep(providerPath, text, effectiveOptions);
        GrepResult mappedResult = new GrepResult(
                result.matches().stream()
                        .map(match -> new GrepMatch(toVirtualPath(match.path()), match.lineNumber(), match.line()))
                        .toList(),
                result.truncated(),
                result.truncationReason(),
                result.searchedMounts(),
                result.searchedFiles()
        );
        log.debug("filesystem provider grep done providerId={} path={} providerPath={} matches={} truncated={} reason={} searchedMounts={} searchedFiles={}",
                provider.providerId(), path, providerPath, mappedResult.matches().size(), mappedResult.truncated(),
                mappedResult.truncationReason(), mappedResult.searchedMounts(), mappedResult.searchedFiles());
        return mappedResult;
    }

    @Override
    public Optional<String> readUrl(String path) {
        String providerPath = toProviderPath(path);
        log.debug("filesystem provider readUrl start providerId={} path={} providerPath={}",
                provider.providerId(), path, providerPath);
        Optional<String> url = provider.readUrl(providerPath);
        log.debug("filesystem provider readUrl done providerId={} path={} providerPath={} present={}",
                provider.providerId(), path, providerPath, url.isPresent());
        return url;
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
