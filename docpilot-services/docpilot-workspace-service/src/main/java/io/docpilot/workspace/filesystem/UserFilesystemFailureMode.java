package io.docpilot.workspace.filesystem;

/**
 * Failure handling strategy for user filesystem retrieval.
 */
public enum UserFilesystemFailureMode {

    /**
     * Convert per-workspace retrieval failures into diagnostics and keep other results.
     */
    BEST_EFFORT,

    /**
     * Propagate the first retrieval failure to the caller.
     */
    STRICT;

    /**
     * Parses an API value into a failure mode, defaulting to best effort.
     *
     * @param value nullable API value.
     * @return parsed failure mode.
     */
    public static UserFilesystemFailureMode parse(String value) {
        if (value == null || value.isBlank()) {
            return BEST_EFFORT;
        }
        try {
            return UserFilesystemFailureMode.valueOf(value.strip().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("failureMode must be BEST_EFFORT or STRICT", exception);
        }
    }

    /**
     * Returns whether per-workspace retrieval failures should be kept as diagnostics.
     */
    public boolean bestEffort() {
        return this == BEST_EFFORT;
    }

}
