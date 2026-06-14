package io.docpilot.filesystem.retrieval;

import java.util.List;

/**
 * Ranked filesystem retrieval output.
 */
public record FilesystemRetrievalResult(
        /**
         * Ranked retrieval hits.
         */
        List<FilesystemRetrievalHit> hits,

        /**
         * Whether results were cut by a limit or child filesystem.
         */
        boolean truncated,

        /**
         * Machine-readable truncation reason.
         */
        String truncationReason,

        /**
         * Leaf mounts consulted while fulfilling this retrieval.
         */
        long searchedMounts
) {

    public static final String TRUNCATED_BY_TOP_K = "topK";
    public static final String TRUNCATED_BY_CHILD = "child";

    public FilesystemRetrievalResult {
        hits = hits == null ? List.of() : List.copyOf(hits);
        if (!truncated) {
            truncationReason = null;
        } else if (truncationReason == null || truncationReason.isBlank()) {
            truncationReason = TRUNCATED_BY_CHILD;
        }
        if (searchedMounts < 0) {
            throw new IllegalArgumentException("searchedMounts must be greater than or equal to 0");
        }
    }

    public static FilesystemRetrievalResult complete(List<FilesystemRetrievalHit> hits) {
        return new FilesystemRetrievalResult(hits, false, null, 0L);
    }

    public static FilesystemRetrievalResult complete(List<FilesystemRetrievalHit> hits, long searchedMounts) {
        return new FilesystemRetrievalResult(hits, false, null, searchedMounts);
    }

    public static FilesystemRetrievalResult truncated(List<FilesystemRetrievalHit> hits,
                                                      String truncationReason,
                                                      long searchedMounts) {
        return new FilesystemRetrievalResult(hits, true, truncationReason, searchedMounts);
    }

}
