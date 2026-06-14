package io.docpilot.workspace.model.request;

/**
 * HTTP request body for user filesystem retrieval.
 *
 * @param path user-visible filesystem path.
 * @param query retrieval query text.
 * @param topK maximum number of hits returned after ranking.
 * @param maxCharsPerHit maximum snippet size per hit.
 * @param failureMode BEST_EFFORT or STRICT.
 */
public record UserFilesystemRetrieveRequest(
        String path,
        String query,
        Integer topK,
        Integer maxCharsPerHit,
        String failureMode
) {
}
