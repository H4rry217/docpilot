package io.docpilot.ai.openai;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Provider-neutral chat request used by OpenAI-compatible adapters.
 */
@Getter
@Setter
public class LlmChatRequest {

    /**
     * Optional model override. When blank, the configured model is used.
     */
    private String model;

    /**
     * Conversation messages in OpenAI-compatible chat format.
     */
    private List<OpenAiChatMessage> messages = new ArrayList<>();

    /**
     * Sampling temperature.
     */
    private Double temperature;

    /**
     * Nucleus sampling value, serialized as top_p.
     */
    private Double topP;

    /**
     * Maximum generated tokens, serialized as max_tokens.
     */
    private Integer maxTokens;

    /**
     * Whether the request should use streaming.
     */
    private Boolean stream;

    /**
     * Optional structured output response format.
     */
    private LlmJsonSchema responseFormat;

    /**
     * Provider-specific extension parameters.
     */
    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

    /**
     * Creates a shallow copy so adapters can fill defaults without mutating caller input.
     */
    public LlmChatRequest copy() {
        LlmChatRequest copy = new LlmChatRequest();
        copy.setModel(model);
        copy.setMessages(messages == null ? null : new ArrayList<>(messages));
        copy.setTemperature(temperature);
        copy.setTopP(topP);
        copy.setMaxTokens(maxTokens);
        copy.setStream(stream);
        copy.setResponseFormat(responseFormat);
        copy.getAdditionalProperties().putAll(additionalProperties);
        return copy;
    }

    /**
     * Builds the OpenAI-compatible wire payload without relying on Jackson field annotations.
     */
    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        putIfNotNull(payload, "model", model);
        putIfNotNull(payload, "messages", messages);
        putIfNotNull(payload, "temperature", temperature);
        putIfNotNull(payload, "top_p", topP);
        putIfNotNull(payload, "max_tokens", maxTokens);
        putIfNotNull(payload, "stream", stream);
        putIfNotNull(payload, "response_format", responseFormat);
        payload.putAll(additionalProperties);
        return payload;
    }

    /**
     * Adds or removes a provider-specific payload field.
     */
    public void setAdditionalProperty(String name, Object value) {
        if (value == null) {
            additionalProperties.remove(name);
            return;
        }
        additionalProperties.put(name, value);
    }

    private static void putIfNotNull(Map<String, Object> payload, String name, Object value) {
        if (value != null) {
            payload.put(name, value);
        }
    }

}
