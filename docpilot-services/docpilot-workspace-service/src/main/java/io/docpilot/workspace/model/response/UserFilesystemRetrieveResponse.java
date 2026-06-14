package io.docpilot.workspace.model.response;

import io.docpilot.filesystem.retrieval.FilesystemRetrievalHit;

import java.util.List;

/**
 * Retrieval context returned from the current user's filesystem.
 *
 * @param hits ranked retrieval hits in user-visible path space.
 * @param truncated whether results were limited by topK or a child retrieval.
 * @param truncationReason machine-readable truncation reason.
 * @param searchedMounts number of workspace mounts consulted.
 * @param diagnostics best-effort diagnostics for skipped retrieval branches.
 */
public record UserFilesystemRetrieveResponse(
        List<FilesystemRetrievalHit> hits,
        boolean truncated,
        String truncationReason,
        long searchedMounts,
        List<UserFilesystemDiagnosticResponse> diagnostics
) {

    public UserFilesystemRetrieveResponse {
        hits = hits == null ? List.of() : List.copyOf(hits);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        if (!truncated) {
            truncationReason = null;
        }
    }

}
