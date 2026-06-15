package io.docpilot.workspace.model.response;

import io.docpilot.workspace.inlinecompletion.InlineCompletionShape;

/**
 * Metadata emitted before inline completion deltas.
 */
public record InlineCompletionMetaResponse(
        String completionId,
        String modelId,
        InlineCompletionShape shape
) {
}
