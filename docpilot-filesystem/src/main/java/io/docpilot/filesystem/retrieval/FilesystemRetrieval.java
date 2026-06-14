package io.docpilot.filesystem.retrieval;

/**
 * Optional semantic retrieval capability exposed by path-first filesystems.
 */
public interface FilesystemRetrieval {

    /**
     * Retrieves semantically relevant snippets under the request path.
     *
     * @param request caller-visible path, query text, and retrieval options.
     * @return ranked retrieval hits with caller-visible paths.
     */
    FilesystemRetrievalResult retrieve(FilesystemRetrievalRequest request);

}
