package io.docpilot.filesystem.retrieval;

/**
 * Generic retrieval controls shared by filesystem implementations.
 */
public record FilesystemRetrievalOptions(
        /**
         * Maximum number of hits to return after ranking.
         */
        Integer topK,

        /**
         * Maximum number of characters kept in each returned snippet.
         */
        Integer maxCharsPerHit
) {

    /**
     * Default hit count when callers do not provide an explicit limit.
     */
    public static final int DEFAULT_TOP_K = 8;

    /**
     * Default snippet size when callers do not provide an explicit limit.
     */
    public static final int DEFAULT_MAX_CHARS_PER_HIT = 500;

    public FilesystemRetrievalOptions {
        topK = normalizeLimit(topK, DEFAULT_TOP_K, "topK");
        maxCharsPerHit = normalizeLimit(maxCharsPerHit, DEFAULT_MAX_CHARS_PER_HIT, "maxCharsPerHit");
    }

    public static FilesystemRetrievalOptions defaults() {
        return new FilesystemRetrievalOptions(null, null);
    }

    public static FilesystemRetrievalOptions effective(FilesystemRetrievalOptions options) {
        return options == null ? defaults() : options;
    }

    private static Integer normalizeLimit(Integer value, int fallback, String name) {
        int normalized = value == null ? fallback : value;
        if (normalized < 0) {
            throw new IllegalArgumentException(name + " must be greater than or equal to 0");
        }
        return normalized;
    }

}
