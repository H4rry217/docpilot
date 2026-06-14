package io.docpilot.filesystem.retrieval;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One filesystem-level retrieval hit.
 */
public record FilesystemRetrievalHit(
        /**
         * Caller-visible file path that contains this hit.
         */
        String path,

        /**
         * Human-readable source title when the implementation can provide one.
         */
        String title,

        /**
         * Text fragment returned to callers as retrieval context.
         */
        String snippet,

        /**
         * Provider ranking score, where larger values should rank earlier.
         */
        Double score,

        /**
         * Heading labels from document root to the hit location.
         */
        List<String> headingPath,

        /**
         * Provider-neutral identifiers and source metadata for advanced callers.
         */
        Map<String, String> metadata
) {

    public FilesystemRetrievalHit {
        headingPath = headingPath == null ? List.of() : List.copyOf(headingPath);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /**
     * Returns a copy of this hit with a different caller-visible path.
     */
    public FilesystemRetrievalHit withPath(String path) {
        return new FilesystemRetrievalHit(
                path,
                title,
                snippet,
                score,
                headingPath,
                new LinkedHashMap<>(metadata)
        );
    }

}
