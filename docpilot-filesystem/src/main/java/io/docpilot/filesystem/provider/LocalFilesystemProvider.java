package io.docpilot.filesystem.provider;

import io.docpilot.filesystem.exception.FilesystemException;
import io.docpilot.filesystem.exception.FileNotFoundException;
import io.docpilot.filesystem.exception.InvalidPathException;
import io.docpilot.filesystem.model.FileEntry;
import io.docpilot.filesystem.model.FileEntryType;
import io.docpilot.filesystem.model.GrepMatch;
import io.docpilot.filesystem.model.GrepOptions;
import io.docpilot.filesystem.model.GrepResult;
import io.docpilot.filesystem.path.FilesystemPath;
import io.docpilot.filesystem.path.GlobMatcher;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

public class LocalFilesystemProvider implements FilesystemProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalFilesystemProvider.class);

    private final String providerId;
    private final Path root;

    public LocalFilesystemProvider(String providerId, Path root) {
        if (StringUtils.isBlank(providerId)) {
            throw new IllegalArgumentException("providerId is required");
        }
        this.providerId = providerId;
        this.root = root.toAbsolutePath().normalize();
        log.debug("local filesystem provider initialized providerId={} root={}", providerId, this.root);
    }

    @Override
    public String providerId() {
        return providerId;
    }

    public Path root() {
        return root;
    }

    @Override
    public List<FileEntry> list(String path) {
        Path localPath = resolveLocal(path);
        log.debug("local filesystem list start providerId={} path={} localPath={}", providerId, path, localPath);
        if (!Files.exists(localPath)) {
            throw new FileNotFoundException("Path not found: " + path);
        }
        if (!Files.isDirectory(localPath)) {
            List<FileEntry> entries = List.of(toEntry(localPath));
            log.debug("local filesystem list done providerId={} path={} localPath={} entries={}",
                    providerId, path, localPath, entries.size());
            return entries;
        }
        try (Stream<Path> children = Files.list(localPath)) {
            List<FileEntry> entries = children
                    .sorted(Comparator.comparing(child -> child.getFileName().toString()))
                    .map(this::toEntry)
                    .toList();
            log.debug("local filesystem list done providerId={} path={} localPath={} entries={}",
                    providerId, path, localPath, entries.size());
            return entries;
        } catch (IOException exception) {
            throw new FilesystemException("Failed to list path: " + path, exception);
        }
    }

    @Override
    public byte[] read(String path) {
        Path localPath = resolveLocal(path);
        log.debug("local filesystem read start providerId={} path={} localPath={}", providerId, path, localPath);
        try {
            byte[] content = Files.readAllBytes(localPath);
            log.debug("local filesystem read done providerId={} path={} localPath={} bytes={}",
                    providerId, path, localPath, content.length);
            return content;
        } catch (IOException exception) {
            throw new FilesystemException("Failed to read path: " + path, exception);
        }
    }

    @Override
    public void write(String path, byte[] content) {
        Path localPath = resolveLocal(path);
        int bytes = content == null ? 0 : content.length;
        log.debug("local filesystem write start providerId={} path={} localPath={} bytes={}",
                providerId, path, localPath, bytes);
        try {
            Path parent = localPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(localPath, content);
            log.debug("local filesystem write done providerId={} path={} localPath={} bytes={}",
                    providerId, path, localPath, bytes);
        } catch (IOException exception) {
            throw new FilesystemException("Failed to write path: " + path, exception);
        }
    }

    @Override
    public void delete(String path) {
        Path localPath = resolveLocal(path);
        log.debug("local filesystem delete start providerId={} path={} localPath={}", providerId, path, localPath);
        if (!Files.exists(localPath)) {
            log.debug("local filesystem delete done providerId={} path={} localPath={} existed=false",
                    providerId, path, localPath);
            return;
        }
        try (Stream<Path> paths = Files.walk(localPath)) {
            List<Path> deleteOrder = paths.sorted(Comparator.reverseOrder()).toList();
            for (Path deletePath : deleteOrder) {
                Files.deleteIfExists(deletePath);
            }
            log.debug("local filesystem delete done providerId={} path={} localPath={} deletedEntries={}",
                    providerId, path, localPath, deleteOrder.size());
        } catch (IOException exception) {
            throw new FilesystemException("Failed to delete path: " + path, exception);
        }
    }

    @Override
    public boolean exists(String path) {
        Path localPath = resolveLocal(path);
        boolean exists = Files.exists(localPath);
        log.debug("local filesystem exists providerId={} path={} localPath={} exists={}",
                providerId, path, localPath, exists);
        return exists;
    }

    @Override
    public FileEntry stat(String path) {
        Path localPath = resolveLocal(path);
        log.debug("local filesystem stat start providerId={} path={} localPath={}", providerId, path, localPath);
        if (!Files.exists(localPath)) {
            throw new FileNotFoundException("Path not found: " + path);
        }
        FileEntry entry = toEntry(localPath);
        log.debug("local filesystem stat done providerId={} path={} localPath={} type={} size={}",
                providerId, path, localPath, entry.type(), entry.size());
        return entry;
    }

    @Override
    public void copy(String sourcePath, String targetPath) {
        Path source = resolveLocal(sourcePath);
        Path target = resolveLocal(targetPath);
        log.debug("local filesystem copy start providerId={} sourcePath={} targetPath={} source={} target={}",
                providerId, sourcePath, targetPath, source, target);
        try {
            long copiedEntries = 0L;
            if (Files.isDirectory(source)) {
                try (Stream<Path> paths = Files.walk(source)) {
                    for (Path current : paths.toList()) {
                        Path relative = source.relativize(current);
                        Path copyTarget = target.resolve(relative).normalize();
                        ensureInsideRoot(copyTarget);
                        if (Files.isDirectory(current)) {
                            Files.createDirectories(copyTarget);
                        } else {
                            Files.createDirectories(copyTarget.getParent());
                            Files.copy(current, copyTarget, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        }
                        copiedEntries++;
                    }
                }
            } else {
                Files.createDirectories(target.getParent());
                Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                copiedEntries = 1L;
            }
            log.debug("local filesystem copy done providerId={} sourcePath={} targetPath={} source={} target={} copiedEntries={}",
                    providerId, sourcePath, targetPath, source, target, copiedEntries);
        } catch (IOException exception) {
            throw new FilesystemException("Failed to copy path: " + sourcePath, exception);
        }
    }

    @Override
    public void move(String sourcePath, String targetPath) {
        Path source = resolveLocal(sourcePath);
        Path target = resolveLocal(targetPath);
        log.debug("local filesystem move start providerId={} sourcePath={} targetPath={} source={} target={}",
                providerId, sourcePath, targetPath, source, target);
        try {
            Files.createDirectories(target.getParent());
            Files.move(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            log.debug("local filesystem move done providerId={} sourcePath={} targetPath={} source={} target={}",
                    providerId, sourcePath, targetPath, source, target);
        } catch (IOException exception) {
            throw new FilesystemException("Failed to move path: " + sourcePath, exception);
        }
    }

    @Override
    public List<FileEntry> glob(String pathPattern) {
        String normalizedPattern = FilesystemPath.normalizeProviderPath(pathPattern);
        String prefix = GlobMatcher.prefixBeforeWildcard(normalizedPattern);
        Path start = resolveLocal(prefix);
        log.debug("local filesystem glob start providerId={} pattern={} normalizedPattern={} prefix={} start={}",
                providerId, pathPattern, normalizedPattern, prefix, start);
        if (!Files.exists(start)) {
            log.debug("local filesystem glob done providerId={} pattern={} normalizedPattern={} entries=0 startExists=false",
                    providerId, pathPattern, normalizedPattern);
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(start)) {
            List<FileEntry> entries = paths
                    .filter(Files::isRegularFile)
                    .map(this::toEntry)
                    .filter(entry -> GlobMatcher.matches(normalizedPattern, entry.path()))
                    .toList();
            log.debug("local filesystem glob done providerId={} pattern={} normalizedPattern={} entries={}",
                    providerId, pathPattern, normalizedPattern, entries.size());
            return entries;
        } catch (IOException exception) {
            throw new FilesystemException("Failed to glob path: " + pathPattern, exception);
        }
    }

    @Override
    public List<GrepMatch> grep(String path, String text) {
        return grep(path, text, GrepOptions.unlimited()).matches();
    }

    @Override
    public GrepResult grep(String path, String text, GrepOptions options) {
        GrepOptions grepOptions = GrepOptions.effective(options);
        Path localPath = resolveLocal(path);
        log.debug("local filesystem grep start providerId={} path={} localPath={} textLength={} maxFiles={} maxMatches={}",
                providerId, path, localPath, text == null ? 0 : text.length(),
                grepOptions.maxFiles(), grepOptions.maxMatches());
        if (!Files.exists(localPath)) {
            log.debug("local filesystem grep done providerId={} path={} localPath={} exists=false matches=0",
                    providerId, path, localPath);
            return GrepResult.complete(List.of());
        }
        if (grepOptions.isMaxMatchesReached(0L)) {
            return GrepResult.truncated(List.of(), GrepResult.TRUNCATED_BY_MAX_MATCHES, 0L, 0L);
        }
        if (grepOptions.isMaxFilesReached(0L)) {
            return GrepResult.truncated(List.of(), GrepResult.TRUNCATED_BY_MAX_FILES, 0L, 0L);
        }

        List<GrepMatch> matches = new ArrayList<>();
        long searchedFiles = 0L;
        String truncationReason = null;

        try (Stream<Path> paths = Files.isDirectory(localPath) ? Files.walk(localPath) : Stream.of(localPath)) {
            // Iterate lazily so file and match limits stop reads instead of trimming after a full scan.
            Iterator<Path> iterator = paths.filter(Files::isRegularFile).iterator();

            while (iterator.hasNext()) {
                if (grepOptions.isMaxFilesReached(searchedFiles)) {
                    truncationReason = GrepResult.TRUNCATED_BY_MAX_FILES;
                    break;
                }
                if (grepOptions.isMaxMatchesReached(matches.size())) {
                    truncationReason = GrepResult.TRUNCATED_BY_MAX_MATCHES;
                    break;
                }

                searchedFiles++;
                if (grepFile(iterator.next(), text, matches, grepOptions)) {
                    truncationReason = GrepResult.TRUNCATED_BY_MAX_MATCHES;
                    break;
                }
            }
        } catch (IOException exception) {
            throw new FilesystemException("Failed to grep path: " + path, exception);
        }

        GrepResult result = new GrepResult(matches, truncationReason != null, truncationReason, 0L, searchedFiles);
        log.debug("local filesystem grep done providerId={} path={} localPath={} matches={} truncated={} reason={} searchedFiles={}",
                providerId, path, localPath, result.matches().size(), result.truncated(),
                result.truncationReason(), result.searchedFiles());
        return result;
    }

    private boolean grepFile(Path file, String text, List<GrepMatch> matches, GrepOptions options) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            String providerPath = toProviderPath(file);

            for (int index = 0; index < lines.size(); index++) {
                if (!lines.get(index).contains(text)) {
                    continue;
                }

                matches.add(new GrepMatch(providerPath, index + 1L, lines.get(index)));
                if (options.isMaxMatchesReached(matches.size())) {
                    return true;
                }
            }

            return false;
        } catch (IOException exception) {
            return false;
        }
    }

    private Path resolveLocal(String providerPath) {
        String normalized = FilesystemPath.normalizeProviderPath(providerPath);
        Path resolved = StringUtils.isEmpty(normalized) ? root : root.resolve(normalized).normalize();
        // Every provider path must stay below root, including paths created by copy/move targets.
        ensureInsideRoot(resolved);
        return resolved;
    }

    private void ensureInsideRoot(Path path) {
        if (!path.toAbsolutePath().normalize().startsWith(root)) {
            throw new InvalidPathException("Path escapes provider root: " + path);
        }
    }

    private FileEntry toEntry(Path localPath) {
        try {
            return new FileEntry(
                    toProviderPath(localPath),
                    localPath.getFileName() == null ? "" : localPath.getFileName().toString(),
                    Files.isDirectory(localPath) ? FileEntryType.DIRECTORY : FileEntryType.FILE,
                    Files.isDirectory(localPath) ? 0L : Files.size(localPath),
                    Files.getLastModifiedTime(localPath).toInstant()
            );
        } catch (IOException exception) {
            throw new FilesystemException("Failed to stat path: " + localPath, exception);
        }
    }

    private String toProviderPath(Path localPath) {
        Path normalized = localPath.toAbsolutePath().normalize();
        ensureInsideRoot(normalized);
        Path relative = root.relativize(normalized);
        return FilesystemPath.toUnixPath(relative.toString());
    }

}
