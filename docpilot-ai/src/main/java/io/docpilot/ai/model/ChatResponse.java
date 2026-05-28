package io.docpilot.ai.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Complete provider-neutral response for a non-streaming chat call.
 */
@Getter
@Setter
public class ChatResponse implements AdditionalPropertiesCarrier {

    /**
     * Provider response id when available.
     */
    private String id;

    /**
     * Provider creation timestamp in epoch seconds.
     */
    private Long createdEpochSecond;

    /**
     * Provider model name used for the response.
     */
    private String model;

    /**
     * Provider backend fingerprint when supplied for diagnostics.
     */
    private String systemFingerprint;

    /**
     * Completed response candidates.
     */
    private List<ChatChoice> choices = new ArrayList<>();

    /**
     * Token usage reported by the provider.
     */
    private ChatUsage usage;

    /**
     * Extension fields preserved from provider responses.
     */
    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

}
