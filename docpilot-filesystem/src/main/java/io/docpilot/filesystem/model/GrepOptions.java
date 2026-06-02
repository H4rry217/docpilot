package io.docpilot.filesystem.model;

public record GrepOptions(
        Integer maxFiles,
        Integer maxMatches
) {

    public GrepOptions {
        maxFiles = normalizeLimit(maxFiles, "maxFiles");
        maxMatches = normalizeLimit(maxMatches, "maxMatches");
    }

    public static GrepOptions unlimited() {
        return new GrepOptions(null, null);
    }

    public static GrepOptions effective(GrepOptions options) {
        return options == null ? unlimited() : options;
    }

    /**
     * Child filesystems receive remaining capacity so they can stop before doing extra work.
     */
    public GrepOptions remainingAfter(long searchedFiles, long matchCount) {
        return new GrepOptions(
                remaining(maxFiles, searchedFiles),
                remaining(maxMatches, matchCount)
        );
    }

    public boolean isMaxFilesReached(long searchedFiles) {
        return maxFiles != null && searchedFiles >= maxFiles;
    }

    public boolean isMaxMatchesReached(long matchCount) {
        return maxMatches != null && matchCount >= maxMatches;
    }

    private static Integer normalizeLimit(Integer limit, String name) {
        if (limit != null && limit < 0) {
            throw new IllegalArgumentException(name + " must be greater than or equal to 0");
        }
        return limit;
    }

    private static Integer remaining(Integer limit, long used) {
        if (limit == null) {
            return null;
        }

        long remaining = limit - used;
        if (remaining <= 0) {
            return 0;
        }

        return remaining > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) remaining;
    }

}
