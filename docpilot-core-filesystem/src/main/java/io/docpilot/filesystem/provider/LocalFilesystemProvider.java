package io.docpilot.filesystem.provider;

import io.docpilot.filesystem.exception.FilesystemException;
import io.docpilot.filesystem.exception.FileNotFoundException;
import io.docpilot.filesystem.exception.InvalidPathException;
import io.docpilot.filesystem.model.FileEntry;
import io.docpilot.filesystem.model.FileEntryType;
import io.docpilot.filesystem.model.GrepMatch;
import io.docpilot.filesystem.path.FilesystemPath;
import io.docpilot.filesystem.path.GlobMatcher;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class LocalFilesystemProvider implements FilesystemProvider {

    private final String providerId;
    private final Path root;

    public LocalFilesystemProvider(String providerId, Path root) {
        if (StringUtils.isBlank(providerId)) {
            throw new IllegalArgumentException("providerId is required");
        }
        this.providerId = providerId;
        this.root = root.toAbsolutePath().normalize();
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
        if (!Files.exists(localPath)) {
            throw new FileNotFoundException("Path not found: " + path);
        }
        if (!Files.isDirectory(localPath)) {
            return List.of(toEntry(localPath));
        }
        try (Stream<Path> children = Files.list(localPath)) {
            return children
                    .sorted(Comparator.comparing(child -> child.getFileName().toString()))
                    .map(this::toEntry)
                    .toList();
        } catch (IOException exception) {
            throw new FilesystemException("Failed to list path: " + path, exception);
        }
    }

    @Override
    public byte[] read(String path) {
        Path localPath = resolveLocal(path);
        try {
            return Files.readAllBytes(localPath);
        } catch (IOException exception) {
            throw new FilesystemException("Failed to read path: " + path, exception);
        }
    }

    @Override
    public void write(String path, byte[] content) {
        Path localPath = resolveLocal(path);
        try {
            Path parent = localPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(localPath, content);
        } catch (IOException exception) {
            throw new FilesystemException("Failed to write path: " + path, exception);
        }
    }

    @Override
    public void delete(String path) {
        Path localPath = resolveLocal(path);
        if (!Files.exists(localPath)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(localPath)) {
            List<Path> deleteOrder = paths.sorted(Comparator.reverseOrder()).toList();
            for (Path deletePath : deleteOrder) {
                Files.deleteIfExists(deletePath);
            }
        } catch (IOException exception) {
            throw new FilesystemException("Failed to delete path: " + path, exception);
        }
    }

    @Override
    public boolean exists(String path) {
        return Files.exists(resolveLocal(path));
    }

    @Override
    public FileEntry stat(String path) {
        Path localPath = resolveLocal(path);
        if (!Files.exists(localPath)) {
            throw new FileNotFoundException("Path not found: " + path);
        }
        return toEntry(localPath);
    }

    @Override
    public void copy(String sourcePath, String targetPath) {
        Path source = resolveLocal(sourcePath);
        Path target = resolveLocal(targetPath);
        try {
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
                    }
                }
            } else {
                Files.createDirectories(target.getParent());
                Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new FilesystemException("Failed to copy path: " + sourcePath, exception);
        }
    }

    @Override
    public void move(String sourcePath, String targetPath) {
        Path source = resolveLocal(sourcePath);
        Path target = resolveLocal(targetPath);
        try {
            Files.createDirectories(target.getParent());
            Files.move(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new FilesystemException("Failed to move path: " + sourcePath, exception);
        }
    }

    @Override
    public List<FileEntry> glob(String pathPattern) {
        String normalizedPattern = FilesystemPath.normalizeProviderPath(pathPattern);
        String prefix = GlobMatcher.prefixBeforeWildcard(normalizedPattern);
        Path start = resolveLocal(prefix);
        if (!Files.exists(start)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(start)) {
            return paths
                    .filter(Files::isRegularFile)
                    .map(this::toEntry)
                    .filter(entry -> GlobMatcher.matches(normalizedPattern, entry.path()))
                    .toList();
        } catch (IOException exception) {
            throw new FilesystemException("Failed to glob path: " + pathPattern, exception);
        }
    }

    @Override
    public List<GrepMatch> grep(String path, String text) {
        Path localPath = resolveLocal(path);
        if (!Files.exists(localPath)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.isDirectory(localPath) ? Files.walk(localPath) : Stream.of(localPath)) {
            return paths
                    .filter(Files::isRegularFile)
                    .flatMap(file -> grepFile(file, text).stream())
                    .toList();
        } catch (IOException exception) {
            throw new FilesystemException("Failed to grep path: " + path, exception);
        }
    }

    private List<GrepMatch> grepFile(Path file, String text) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            String providerPath = toProviderPath(file);
            return java.util.stream.IntStream.range(0, lines.size())
                    .filter(index -> lines.get(index).contains(text))
                    .mapToObj(index -> new GrepMatch(providerPath, index + 1L, lines.get(index)))
                    .toList();
        } catch (IOException exception) {
            return List.of();
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
