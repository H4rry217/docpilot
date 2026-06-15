package io.docpilot.workspace.model.response;

/**
 * One ranked inline completion candidate returned to the editor.
 *
 * @param index zero-based candidate order inside one completion response.
 * @param markdown markdown fragment to insert if the user accepts this candidate.
 * @param previewText plain text shown as ghost text and in the candidate menu.
 */
public record InlineCompletionCandidateResponse(
        Integer index,
        String markdown,
        String previewText
) {
}
