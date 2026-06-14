package io.docpilot.filesystem;

/**
 * Operations that a composite mount may expose to callers.
 */
public enum FilesystemCapability {

    LIST,
    READ,
    STAT,
    SEARCH,
    /**
     * Semantic retrieval capability exposed through FilesystemRetrieval.
     */
    RETRIEVE,
    READ_URL,
    WRITE,
    DELETE,
    COPY,
    MOVE

}
