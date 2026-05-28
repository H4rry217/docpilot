package io.docpilot.ai.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Provider-neutral chat request.
 *
 * <p>Adapters translate this shape into provider wire payloads and may merge {@link #options}
 * for provider-specific fields that DocPilot does not model directly.
 */
@Getter
@Setter
public class ChatRequest {

    /**
     * Optional model override. When blank, the configured model is used.
     */
    private String model;

    /**
     * Ordered conversation messages sent to the model.
     */
    private List<ChatMessage> messages = new ArrayList<>();

    /**
     * Sampling temperature.
     */
    private Double temperature;

    /**
     * Nucleus sampling value.
     */
    private Double topP;

    /**
     * Maximum generated tokens.
     */
    private Integer maxOutputTokens;

    /**
     * Optional structured output response format.
     */
    private ChatResponseFormat responseFormat;

    /**
     * Provider-specific options that adapters may merge into their wire payloads.
     */
    private final Map<String, Object> options = new LinkedHashMap<>();

    /**
     * Creates a shallow copy so adapters can fill defaults without mutating caller input.
     */
    public ChatRequest copy() {
        ChatRequest copy = new ChatRequest();
        copy.setModel(model);
        copy.setMessages(messages == null ? null : new ArrayList<>(messages));
        copy.setTemperature(temperature);
        copy.setTopP(topP);
        copy.setMaxOutputTokens(maxOutputTokens);
        copy.setResponseFormat(responseFormat);
        copy.getOptions().putAll(options);
        return copy;
    }

    /**
     * Adds or removes a provider-specific option.
     */
    public void setOption(String name, Object value) {
        if (value == null) {
            options.remove(name);
            return;
        }
        options.put(name, value);
    }

}
