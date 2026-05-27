package io.docpilot.filesystem.path;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

public final class GlobMatcher {

    public static boolean matches(String pattern, String path) {
        return FilenameUtils.wildcardMatch(FilesystemPath.toUnixPath(path), FilesystemPath.toUnixPath(pattern));
    }

    public static String prefixBeforeWildcard(String pattern) {
        String normalized = FilesystemPath.toUnixPath(StringUtils.defaultString(pattern));
        int wildcard = firstWildcardIndex(normalized);
        if (wildcard < 0) {
            return FilesystemPath.normalizeProviderPath(normalized);
        }
        int slash = normalized.lastIndexOf(FilesystemPathNames.SEPARATOR_CHAR, wildcard);
        if (slash < 0) {
            return FilesystemPathNames.EMPTY_PATH;
        }
        return FilesystemPath.normalizeProviderPath(normalized.substring(0, slash));
    }

    private static int firstWildcardIndex(String value) {
        int result = -1;
        for (char wildcard : new char[]{'*', '?'}) {
            int index = value.indexOf(wildcard);
            if (index >= 0 && (result < 0 || index < result)) {
                result = index;
            }
        }
        return result;
    }

}
