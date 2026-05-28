package io.docpilot.ai.provider.openai;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wire DTO for OpenAI-compatible streaming deltas.
 */
@Getter
@Setter
public class OpenAiChatDelta {

    private String role;
    private Object content;
    private Object toolCalls;
    private Object reasoningContent;

    /**
     * Captures provider extension fields without blocking deserialization.
     */
    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

}
