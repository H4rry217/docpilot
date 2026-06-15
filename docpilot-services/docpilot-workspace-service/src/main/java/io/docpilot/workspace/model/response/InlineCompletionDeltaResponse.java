package io.docpilot.workspace.model.response;

/**
 * Incremental markdown emitted by the inline completion stream.
 */
public record InlineCompletionDeltaResponse(String markdownDelta) {
}
