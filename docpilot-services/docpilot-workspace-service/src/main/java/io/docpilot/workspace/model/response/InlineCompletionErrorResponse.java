package io.docpilot.workspace.model.response;

/**
 * Error payload emitted as an SSE event once the stream has already started.
 */
public record InlineCompletionErrorResponse(String code, String message) {
}
