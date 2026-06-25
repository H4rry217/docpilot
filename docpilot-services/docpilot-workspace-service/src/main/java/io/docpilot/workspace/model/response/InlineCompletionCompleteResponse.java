package io.docpilot.workspace.model.response;

import io.docpilot.workspace.inlinecompletion.InlineCompletionShape;

import java.util.List;

/**
 * Non-streaming inline completion result with multiple candidates for one cursor anchor.
 *
 * @param completionId server-generated id used to correlate logs for this completion request.
 * @param modelId configured model id that produced the candidates.
 * @param shape server-decided completion shape used for prompting and insertion handling.
 * @param candidates ranked insertion candidates. The first item is the default ghost text.
 * @param diagnostics best-effort retrieval diagnostics that did not block generation.
 */
public record InlineCompletionCompleteResponse(
        String completionId,
        String modelId,
        InlineCompletionShape shape,
        List<InlineCompletionCandidateResponse> candidates,
        List<UserFilesystemDiagnosticResponse> diagnostics
) {

    public InlineCompletionCompleteResponse {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

}
