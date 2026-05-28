package io.docpilot.ai.provider.openai;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wire DTO for one OpenAI-compatible response choice.
 */
@Getter
@Setter
public class OpenAiChoice {

    private Integer index;
    private OpenAiChatMessage message;
    private OpenAiChatDelta delta;
    private String finishReason;
    private Object logprobs;
    /**
     * Captures provider extension fields without blocking deserialization.
     */
    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

}
