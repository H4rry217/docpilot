package io.docpilot.ai.model;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One completed candidate returned by a non-streaming chat request.
 */
@Getter
@Setter
public class ChatChoice implements AdditionalPropertiesCarrier {

    /**
     * Provider-supplied candidate index.
     */
    private Integer index;

    /**
     * Completed assistant message for this choice.
     */
    private ChatMessage message;

    /**
     * Provider finish reason, for example stop, length, or tool_calls.
     */
    private String finishReason;

    /**
     * Optional token log-probability payload when a provider returns one.
     */
    private Object logprobs;

    /**
     * Extension fields preserved from provider responses.
     */
    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

}
