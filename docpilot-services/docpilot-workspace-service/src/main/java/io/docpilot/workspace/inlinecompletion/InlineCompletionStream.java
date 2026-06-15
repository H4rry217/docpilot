package io.docpilot.workspace.inlinecompletion;

import io.docpilot.ai.model.ChatStreamEvent;
import io.docpilot.workspace.model.response.UserFilesystemDiagnosticResponse;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Prepared inline completion stream plus metadata needed by the web transport.
 */
public record InlineCompletionStream(
        String completionId,
        String modelId,
        InlineCompletionShape shape,
        List<UserFilesystemDiagnosticResponse> diagnostics,
        Flux<ChatStreamEvent> events
) {

    public InlineCompletionStream {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        events = events == null ? Flux.empty() : events;
    }

}
