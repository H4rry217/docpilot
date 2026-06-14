package io.docpilot.filesystem.retrieval;

import io.docpilot.filesystem.path.FilesystemPath;
import io.docpilot.filesystem.path.FilesystemPathNames;
import org.apache.commons.lang3.StringUtils;

/**
 * Filesystem retrieval request expressed in caller-visible path terms.
 */
public record FilesystemRetrievalRequest(
        /**
         * Caller-visible path that scopes retrieval.
         */
        String path,

        /**
         * Natural language or keyword query text.
         */
        String query,

        /**
         * Generic retrieval limits and filters.
         */
        FilesystemRetrievalOptions options
) {

    public FilesystemRetrievalRequest {
        path = FilesystemPath.normalizeVirtualPath(StringUtils.defaultIfBlank(path, FilesystemPathNames.ROOT));
        query = query == null ? "" : query;
        options = FilesystemRetrievalOptions.effective(options);
    }

}
