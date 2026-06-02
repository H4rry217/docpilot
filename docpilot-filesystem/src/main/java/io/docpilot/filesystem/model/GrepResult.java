package io.docpilot.filesystem.model;

import java.util.List;

public record GrepResult(
        List<GrepMatch> matches,
        boolean truncated,
        String truncationReason,
        long searchedMounts,
    long searchedFiles
) {

    public static final String TRUNCATED_BY_MAX_FILES = "maxFiles";
    public static final String TRUNCATED_BY_MAX_MATCHES = "maxMatches";
    public static final String TRUNCATED_BY_CHILD = "child";

    public GrepResult {
        matches = matches == null ? List.of() : List.copyOf(matches);
        if (!truncated) {
            truncationReason = null;
        } else if (truncationReason == null || truncationReason.isBlank()) {
            truncationReason = TRUNCATED_BY_CHILD;
        }
        if (searchedMounts < 0) {
            throw new IllegalArgumentException("searchedMounts must be greater than or equal to 0");
        }
        if (searchedFiles < 0) {
            throw new IllegalArgumentException("searchedFiles must be greater than or equal to 0");
        }
    }

    public static GrepResult complete(List<GrepMatch> matches) {
        return new GrepResult(matches, false, null, 0L, 0L);
    }

    public static GrepResult complete(List<GrepMatch> matches, long searchedMounts, long searchedFiles) {
        return new GrepResult(matches, false, null, searchedMounts, searchedFiles);
    }

    public static GrepResult truncated(List<GrepMatch> matches,
                                       String truncationReason,
                                       long searchedMounts,
                                       long searchedFiles) {
        return new GrepResult(matches, true, truncationReason, searchedMounts, searchedFiles);
    }

}
