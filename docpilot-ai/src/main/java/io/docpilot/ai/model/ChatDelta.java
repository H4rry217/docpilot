package io.docpilot.ai.model;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Incremental assistant update carried by a {@link ChatStreamEvent}.
 */
@Getter
@Setter
public class ChatDelta implements AdditionalPropertiesCarrier {

    /**
     * Role delta, usually emitted once near the beginning of a stream.
     */
    private String role;

    /**
     * Partial message content emitted by the provider.
     */
    private Object content;

    /**
     * Partial tool-call payload emitted by the provider.
     */
    private Object toolCalls;

    /**
     * Partial reasoning text emitted by thinking-mode providers.
     */
    private Object reasoningContent;

    /**
     * Extension fields preserved from provider stream events.
     */
    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

}
