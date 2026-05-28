package io.docpilot.ai.provider.openai;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wire DTO for OpenAI-compatible chat completion and stream chunk responses.
 *
 * <p>This type stays inside the provider adapter; public DocPilot callers should use
 * {@link io.docpilot.ai.model.ChatResponse} or {@link io.docpilot.ai.model.ChatStreamEvent}.
 */
@Getter
@Setter
public class OpenAiChatCompletionResponse {

    private String id;
    private String object;
    private Long created;
    private String model;
    private String systemFingerprint;
    private List<OpenAiChoice> choices = new ArrayList<>();
    private OpenAiUsage usage;
    /**
     * Captures provider extension fields without blocking deserialization.
     */
    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

}
