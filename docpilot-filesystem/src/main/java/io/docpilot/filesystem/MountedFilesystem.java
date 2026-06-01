package io.docpilot.filesystem;

import io.docpilot.filesystem.model.FileEntry;
import io.docpilot.filesystem.model.GrepMatch;
import io.docpilot.filesystem.path.FilesystemPath;
import io.docpilot.filesystem.path.FilesystemPathNames;
import org.apache.commons.lang3.StringUtils;

/**
 * One mount edge from a composite path to a target filesystem path.
 */
public record MountedFilesystem(
        String mountPath,
        Filesystem filesystem,
        String targetRoot,
        MountOptions options
) {

    public MountedFilesystem {
        if (filesystem == null) {
            throw new IllegalArgumentException("filesystem is required");
        }

        mountPath = FilesystemPath.normalizeVirtualPath(mountPath);
        targetRoot = FilesystemPath.normalizeVirtualPath(StringUtils.defaultIfBlank(targetRoot, FilesystemPathNames.ROOT));
        options = options == null ? MountOptions.readOnly() : options;
    }

    public boolean matches(String path) {
        String normalizedPath = FilesystemPath.normalizeVirtualPath(path);
        return FilesystemPath.isSameOrDescendant(mountPath, normalizedPath);
    }

    public String toTargetPath(String path) {
        // Strip the mount prefix, then rebase the suffix under the target root.
        String relativePath = FilesystemPath.relativeVirtualPath(mountPath, path);
        return FilesystemPath.joinVirtualPath(targetRoot, relativePath);
    }

    public String toTargetPattern(String pattern) {
        // Glob patterns use the same rebasing rule as normal paths.
        String normalizedPattern = FilesystemPath.normalizeGlobPattern(pattern);
        String relativePattern = FilesystemPath.relativeVirtualPattern(mountPath, normalizedPattern);

        return FilesystemPath.joinVirtualPattern(targetRoot, relativePattern);
    }

    public String toMountPath(String targetPath) {
        // Convert target filesystem results back into the caller-visible mount path.
        String relativePath = FilesystemPath.relativeVirtualPath(targetRoot, targetPath);
        return FilesystemPath.joinVirtualPath(mountPath, relativePath);
    }

    public FileEntry toMountEntry(FileEntry entry) {
        return new FileEntry(
                toMountPath(entry.path()),
                entry.name(),
                entry.type(),
                entry.size(),
                entry.lastModified()
        );
    }

    public GrepMatch toMountMatch(GrepMatch match) {
        return new GrepMatch(toMountPath(match.path()), match.lineNumber(), match.line());
    }

}
