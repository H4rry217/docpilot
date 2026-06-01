package io.docpilot.filesystem;

import io.docpilot.filesystem.exception.FileNotFoundException;
import io.docpilot.filesystem.exception.UnsupportedFilesystemOperationException;
import io.docpilot.filesystem.model.FileEntry;
import io.docpilot.filesystem.model.FileEntryType;
import io.docpilot.filesystem.model.GrepMatch;
import io.docpilot.filesystem.path.FilesystemPath;
import io.docpilot.filesystem.path.FilesystemPathNames;
import io.docpilot.filesystem.path.GlobMatcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Filesystem implementation that delegates paths to mounted child filesystems.
 */
public class CompositeFilesystem implements Filesystem {

    private final List<MountedFilesystem> mounts = new CopyOnWriteArrayList<>();

    public CompositeFilesystem mount(String mountPath, Filesystem filesystem) {
        return mount(mountPath, filesystem, FilesystemPathNames.ROOT, MountOptions.readOnly());
    }

    public CompositeFilesystem mount(String mountPath, Filesystem filesystem, String targetRoot, MountOptions options) {
        MountedFilesystem mounted = new MountedFilesystem(mountPath, filesystem, targetRoot, options);

        if (mounts.stream().anyMatch(existing -> existing.mountPath().equals(mounted.mountPath()))) {
            throw new IllegalArgumentException("Filesystem mount already exists: " + mounted.mountPath());
        }

        // Prevent recursive mount graphs such as A -> B -> A.
        if (filesystem == this || filesystem instanceof CompositeFilesystem composite
                && composite.containsFilesystem(this, Collections.newSetFromMap(new IdentityHashMap<>()))) {
            throw new IllegalArgumentException("Filesystem mount would create a cycle: " + mounted.mountPath());
        }

        mounts.add(mounted);
        return this;
    }

    public List<MountedFilesystem> mounts() {
        return List.copyOf(mounts);
    }

    @Override
    public List<FileEntry> list(String path) {
        String normalizedPath = FilesystemPath.normalizeVirtualPath(path);
        Map<String, FileEntry> entries = new LinkedHashMap<>();
        boolean resolved = false;

        Optional<MountedFilesystem> mounted = resolve(normalizedPath);
        if (mounted.isPresent()) {
            MountedFilesystem filesystem = mounted.get();
            require(filesystem, FilesystemCapability.LIST);

            try {
                filesystem.filesystem().list(filesystem.toTargetPath(normalizedPath)).stream()
                        .map(filesystem::toMountEntry)
                        .forEach(entry -> entries.put(entry.path(), entry));
                resolved = true;
            } catch (FileNotFoundException exception) {
                // A concrete mount may not contain the path, but child mounts can still create it synthetically.
                if (syntheticChildren(normalizedPath).isEmpty()) {
                    throw exception;
                }
            }
        }

        // Mount ancestors are visible as directories even when no child filesystem owns that exact path.
        syntheticChildren(normalizedPath).forEach(entry -> entries.putIfAbsent(entry.path(), entry));

        if (!resolved && entries.isEmpty()) {
            throw new FileNotFoundException("Path not found: " + normalizedPath);
        }

        return entries.values().stream()
                .sorted(Comparator.comparing(FileEntry::path))
                .toList();
    }

    @Override
    public byte[] read(String path) {
        String normalizedPath = FilesystemPath.normalizeVirtualPath(path);
        MountedFilesystem mounted = requireResolved(normalizedPath);
        require(mounted, FilesystemCapability.READ);
        return mounted.filesystem().read(mounted.toTargetPath(normalizedPath));
    }

    @Override
    public void write(String path, byte[] content) {
        String normalizedPath = FilesystemPath.normalizeVirtualPath(path);
        MountedFilesystem mounted = requireResolved(normalizedPath);
        require(mounted, FilesystemCapability.WRITE);
        mounted.filesystem().write(mounted.toTargetPath(normalizedPath), content);
    }

    @Override
    public void delete(String path) {
        String normalizedPath = FilesystemPath.normalizeVirtualPath(path);
        MountedFilesystem mounted = requireResolved(normalizedPath);
        require(mounted, FilesystemCapability.DELETE);
        mounted.filesystem().delete(mounted.toTargetPath(normalizedPath));
    }

    @Override
    public boolean exists(String path) {
        try {
            stat(path);
            return true;
        } catch (FileNotFoundException exception) {
            return false;
        }
    }

    @Override
    public FileEntry stat(String path) {
        String normalizedPath = FilesystemPath.normalizeVirtualPath(path);
        Optional<MountedFilesystem> mounted = resolve(normalizedPath);

        if (mounted.isPresent()) {
            MountedFilesystem filesystem = mounted.get();
            require(filesystem, FilesystemCapability.STAT);

            try {
                return filesystem.toMountEntry(filesystem.filesystem().stat(filesystem.toTargetPath(normalizedPath)));
            } catch (FileNotFoundException exception) {
                if (!syntheticChildren(normalizedPath).isEmpty()) {
                    return syntheticDirectory(normalizedPath);
                }
                throw exception;
            }
        }

        if (!syntheticChildren(normalizedPath).isEmpty()) {
            return syntheticDirectory(normalizedPath);
        }

        throw new FileNotFoundException("Path not found: " + normalizedPath);
    }

    @Override
    public void copy(String sourcePath, String targetPath) {
        String normalizedSourcePath = FilesystemPath.normalizeVirtualPath(sourcePath);
        String normalizedTargetPath = FilesystemPath.normalizeVirtualPath(targetPath);
        MountedFilesystem source = requireResolved(normalizedSourcePath);
        MountedFilesystem target = requireResolved(normalizedTargetPath);

        require(source, FilesystemCapability.READ);
        require(target, FilesystemCapability.WRITE);

        if (source.filesystem() == target.filesystem()) {
            source.filesystem().copy(source.toTargetPath(normalizedSourcePath), target.toTargetPath(normalizedTargetPath));
            return;
        }

        target.filesystem().write(target.toTargetPath(normalizedTargetPath),
                source.filesystem().read(source.toTargetPath(normalizedSourcePath)));
    }

    @Override
    public void move(String sourcePath, String targetPath) {
        String normalizedSourcePath = FilesystemPath.normalizeVirtualPath(sourcePath);
        MountedFilesystem source = requireResolved(normalizedSourcePath);
        require(source, FilesystemCapability.DELETE);
        copy(normalizedSourcePath, targetPath);
        source.filesystem().delete(source.toTargetPath(normalizedSourcePath));
    }

    @Override
    public List<FileEntry> glob(String pathPattern) {
        String normalizedPattern = FilesystemPath.normalizeGlobPattern(pathPattern);
        String staticPrefix = FilesystemPath.staticPrefixForGlob(normalizedPattern);
        List<FileEntry> matches = new ArrayList<>();

        walkGlob(staticPrefix, normalizedPattern, matches, new HashSet<>());

        return matches.stream()
                .sorted(Comparator.comparing(FileEntry::path))
                .toList();
    }

    @Override
    public List<GrepMatch> grep(String path, String text) {
        String normalizedPath = FilesystemPath.normalizeVirtualPath(path);
        MountedFilesystem mounted = requireResolved(normalizedPath);

        require(mounted, FilesystemCapability.SEARCH);

        return mounted.filesystem().grep(mounted.toTargetPath(normalizedPath), text).stream()
                .map(mounted::toMountMatch)
                .toList();
    }

    @Override
    public Optional<String> readUrl(String path) {
        String normalizedPath = FilesystemPath.normalizeVirtualPath(path);
        MountedFilesystem mounted = requireResolved(normalizedPath);

        require(mounted, FilesystemCapability.READ_URL);

        return mounted.filesystem().readUrl(mounted.toTargetPath(normalizedPath));
    }

    private Optional<MountedFilesystem> resolve(String normalizedPath) {
        // Longest prefix wins: /project/tmp should beat /project.
        return mounts.stream()
                .filter(mount -> mount.matches(normalizedPath))
                .max(Comparator.comparingInt(mount -> mount.mountPath().length()));
    }

    private MountedFilesystem requireResolved(String normalizedPath) {
        return resolve(normalizedPath)
                .orElseThrow(() -> new FileNotFoundException("No filesystem mount for " + normalizedPath));
    }

    private void require(MountedFilesystem mounted, FilesystemCapability capability) {
        if (!mounted.options().allows(capability)) {
            throw new UnsupportedFilesystemOperationException(
                    "Filesystem mount " + mounted.mountPath() + " does not allow " + capability
            );
        }
    }

    private List<FileEntry> syntheticChildren(String normalizedPath) {
        Map<String, FileEntry> entries = new LinkedHashMap<>();

        for (MountedFilesystem mounted : mounts) {
            if (!FilesystemPath.isStrictAncestor(normalizedPath, mounted.mountPath())) {
                continue;
            }

            String relativePath = FilesystemPath.relativeVirtualPath(normalizedPath, mounted.mountPath());
            String childName = relativePath.split(FilesystemPathNames.ROOT, 2)[0];
            String childPath = FilesystemPath.joinVirtualPath(normalizedPath, childName);

            entries.putIfAbsent(childPath, new FileEntry(childPath, childName, FileEntryType.DIRECTORY, 0L, null));
        }

        return new ArrayList<>(entries.values());
    }

    private void walkGlob(String path, String normalizedPattern, List<FileEntry> matches, Set<String> visited) {
        String normalizedPath = FilesystemPath.normalizeVirtualPath(path);

        if (!visited.add(normalizedPath)) {
            return;
        }

        Optional<MountedFilesystem> mounted = resolve(normalizedPath);
        mounted.ifPresent(filesystem -> require(filesystem, FilesystemCapability.SEARCH));

        FileEntry entry = stat(normalizedPath);
        if (entry.type() == FileEntryType.FILE) {
            if (GlobMatcher.matches(normalizedPattern, entry.path())) {
                matches.add(entry);
            }
            return;
        }

        // Walk through normal and synthetic directories so a broad glob can span nested mounts.
        for (FileEntry child : list(normalizedPath)) {
            if (child.type() == FileEntryType.DIRECTORY) {
                walkGlob(child.path(), normalizedPattern, matches, visited);
                continue;
            }

            if (GlobMatcher.matches(normalizedPattern, child.path())) {
                matches.add(child);
            }
        }
    }

    private FileEntry syntheticDirectory(String path) {
        String normalizedPath = FilesystemPath.normalizeVirtualPath(path);
        return new FileEntry(
                normalizedPath,
                FilesystemPath.nameOf(normalizedPath),
                FileEntryType.DIRECTORY,
                0L,
                null
        );
    }

    private boolean containsFilesystem(Filesystem filesystem, Set<CompositeFilesystem> visited) {
        if (!visited.add(this)) {
            return false;
        }

        for (MountedFilesystem mount : mounts) {
            if (mount.filesystem() == filesystem) {
                return true;
            }

            if (mount.filesystem() instanceof CompositeFilesystem composite
                    && composite.containsFilesystem(filesystem, visited)) {
                return true;
            }
        }

        return false;
    }

}
