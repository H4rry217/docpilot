package io.docpilot.ai.provider.openai;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wire DTO for OpenAI-compatible token usage.
 */
@Getter
@Setter
public class OpenAiUsage {

    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Object promptTokensDetails;
    private Object completionTokensDetails;
    /**
     * Captures provider extension fields without blocking deserialization.
     */
    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

}
