package io.docpilot.workspace.model.response;

import io.docpilot.workspace.inlinecompletion.InlineCompletionShape;

import java.util.List;

/**
 * Final inline completion payload.
 */
public record InlineCompletionDoneResponse(
        String markdown,
        String previewText,
        InlineCompletionShape shape,
        List<UserFilesystemDiagnosticResponse> diagnostics
) {

    public InlineCompletionDoneResponse {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

}
