package io.docpilot.filesystem.path;

import io.docpilot.filesystem.exception.InvalidPathException;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

public final class FilesystemPath {

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

    public static String joinVirtualPath(String root, String relativePath) {
        String normalizedRoot = normalizeVirtualPath(root);
        String normalizedRelative = normalizeProviderPath(relativePath);

        if (StringUtils.isEmpty(normalizedRelative)) {
            return normalizedRoot;
        }

        if (FilesystemPathNames.ROOT.equals(normalizedRoot)) {
            return FilesystemPathNames.ROOT + normalizedRelative;
        }

        return normalizedRoot + FilesystemPathNames.ROOT + normalizedRelative;
    }

    public static String joinVirtualPattern(String root, String relativePattern) {
        String normalizedRoot = normalizeVirtualPath(root);
        String normalizedRelative = normalizeProviderPath(relativePattern);

        if (StringUtils.isEmpty(normalizedRelative)) {
            return normalizedRoot;
        }

        if (FilesystemPathNames.ROOT.equals(normalizedRoot)) {
            return FilesystemPathNames.ROOT + normalizedRelative;
        }

        return normalizedRoot + FilesystemPathNames.ROOT + normalizedRelative;
    }

    public static String relativeVirtualPath(String parent, String path) {
        String normalizedParent = normalizeVirtualPath(parent);
        String normalizedPath = normalizeVirtualPath(path);

        if (FilesystemPathNames.ROOT.equals(normalizedParent)) {
            return normalizedPath.substring(1);
        }

        if (normalizedPath.equals(normalizedParent)) {
            return FilesystemPathNames.EMPTY_PATH;
        }

        if (normalizedPath.startsWith(normalizedParent + FilesystemPathNames.ROOT)) {
            return normalizedPath.substring(normalizedParent.length() + 1);
        }

        throw new InvalidPathException("Path is not under parent: " + path);
    }

    public static String relativeVirtualPattern(String parent, String pattern) {
        String normalizedParent = normalizeVirtualPath(parent);
        String normalizedPattern = normalizeGlobPattern(pattern);

        if (FilesystemPathNames.ROOT.equals(normalizedParent)) {
            return normalizedPattern.substring(1);
        }

        if (normalizedPattern.equals(normalizedParent)) {
            return FilesystemPathNames.EMPTY_PATH;
        }

        if (normalizedPattern.startsWith(normalizedParent + FilesystemPathNames.ROOT)) {
            return normalizedPattern.substring(normalizedParent.length() + 1);
        }

        throw new InvalidPathException("Pattern is not under parent: " + pattern);
    }

    /**
     * Prefix test for mount lookup. Root is considered an ancestor of every virtual path.
     */
    public static boolean isSameOrDescendant(String parent, String path) {
        String normalizedParent = normalizeVirtualPath(parent);
        String normalizedPath = normalizeVirtualPath(path);

        return FilesystemPathNames.ROOT.equals(normalizedParent)
                || normalizedPath.equals(normalizedParent)
                || normalizedPath.startsWith(normalizedParent + FilesystemPathNames.ROOT);
    }

    public static boolean isStrictAncestor(String parent, String path) {
        String normalizedParent = normalizeVirtualPath(parent);
        String normalizedPath = normalizeVirtualPath(path);

        if (normalizedPath.equals(normalizedParent)) {
            return false;
        }

        return isSameOrDescendant(normalizedParent, normalizedPath);
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
