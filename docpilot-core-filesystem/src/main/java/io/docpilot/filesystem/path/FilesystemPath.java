package io.docpilot.filesystem.path;

import io.docpilot.filesystem.exception.InvalidPathException;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

public final class FilesystemPath {

    private FilesystemPath() {
    }

    public static String normalizeVirtualPath(String path) {
        if (StringUtils.isBlank(path)) {
            throw new InvalidPathException("Path is required");
        }
        String normalized = toUnixPath(path);
        if (!normalized.startsWith(FilesystemPathNames.ROOT)) {
            normalized = FilesystemPathNames.ROOT + normalized;
        }
        String joined = FilesystemPathNames.ROOT + String.join(FilesystemPathNames.ROOT, cleanSegments(normalized));
        return joined.length() > 1 && joined.endsWith(FilesystemPathNames.ROOT) ? joined.substring(0, joined.length() - 1) : joined;
    }

    public static String normalizeProviderPath(String path) {
        if (StringUtils.isBlank(path) || FilesystemPathNames.ROOT.equals(StringUtils.trim(path))) {
            return FilesystemPathNames.EMPTY_PATH;
        }
        String normalized = toUnixPath(path);
        if (normalized.startsWith(FilesystemPathNames.ROOT)) {
            normalized = normalized.substring(1);
        }
        return String.join(FilesystemPathNames.ROOT, cleanSegments(normalized));
    }

    public static String joinProviderPath(String root, String relativePath) {
        String normalizedRoot = normalizeProviderPath(root);
        String normalizedRelative = normalizeProviderPath(relativePath);
        if (StringUtils.isEmpty(normalizedRoot)) {
            return normalizedRelative;
        }
        if (StringUtils.isEmpty(normalizedRelative)) {
            return normalizedRoot;
        }
        return normalizedRoot + FilesystemPathNames.ROOT + normalizedRelative;
    }

    public static String nameOf(String path) {
        String normalized = StringUtils.isBlank(path) ? FilesystemPathNames.EMPTY_PATH : toUnixPath(path);
        if (normalized.endsWith(FilesystemPathNames.ROOT)) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return FilenameUtils.getName(normalized);
    }

    public static String normalizeGlobPattern(String pattern) {
        if (StringUtils.isBlank(pattern)) {
            throw new InvalidPathException("Glob pattern is required");
        }
        String normalized = toUnixPath(pattern);
        if (!normalized.startsWith(FilesystemPathNames.ROOT)) {
            normalized = FilesystemPathNames.ROOT + normalized;
        }
        for (String segment : normalized.split(FilesystemPathNames.MULTIPLE_SEPARATOR_REGEX)) {
            if (FilesystemPathNames.PARENT_DIRECTORY.equals(segment)) {
                throw new InvalidPathException("Path must not contain '..': " + pattern);
            }
        }
        return normalized.replaceAll(FilesystemPathNames.DUPLICATE_SEPARATOR_REGEX, FilesystemPathNames.ROOT);
    }

    public static String staticPrefixForGlob(String pattern) {
        String normalized = normalizeGlobPattern(pattern);
        int wildcardIndex = firstWildcardIndex(normalized);
        if (wildcardIndex < 0) {
            return normalizeVirtualPath(normalized);
        }
        int slashIndex = normalized.lastIndexOf(FilesystemPathNames.SEPARATOR_CHAR, wildcardIndex);
        if (slashIndex <= 0) {
            return FilesystemPathNames.ROOT;
        }
        return normalizeVirtualPath(normalized.substring(0, slashIndex));
    }

    public static boolean hasWildcard(String pattern) {
        return firstWildcardIndex(pattern) >= 0;
    }

    private static int firstWildcardIndex(String value) {
        int result = -1;
        for (char wildcard : new char[]{'*', '?', '[', '{'}) {
            int index = value.indexOf(wildcard);
            if (index >= 0 && (result < 0 || index < result)) {
                result = index;
            }
        }
        return result;
    }

    private static List<String> cleanSegments(String path) {
        List<String> segments = new ArrayList<>();
        for (String segment : path.split(FilesystemPathNames.MULTIPLE_SEPARATOR_REGEX)) {
            if (StringUtils.isBlank(segment) || FilesystemPathNames.CURRENT_DIRECTORY.equals(segment)) {
                continue;
            }
            if (FilesystemPathNames.PARENT_DIRECTORY.equals(segment)) {
                throw new InvalidPathException("Path must not contain '..': " + path);
            }
            segments.add(segment);
        }
        return segments;
    }

    public static String toUnixPath(String path) {
        return FilenameUtils.separatorsToUnix(path);
    }

}
