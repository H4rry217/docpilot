package io.docpilot.ai;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.experimental.Accessors;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provider-neutral metadata for a configured AI model.
 *
 * <p>The values describe what DocPilot can know at configuration time. Provider adapters should
 * not guess model capabilities; unknown limits can remain null until configured or discovered.
 */
@Getter
@Accessors(fluent = true)
public class AiModelMetadata {

    /**
     * Registry id used to resolve this configured model.
     */
    private final String id;

    /**
     * Provider adapter id, for example openai-compatible.
     */
    private final String provider;

    /**
     * Provider-facing model name sent on requests.
     */
    private final String modelName;

    /**
     * Human-readable label for UI and diagnostics.
     */
    private final String displayName;

    /**
     * Maximum total context window in tokens when known.
     */
    private final Integer contextWindowTokens;

    /**
     * Maximum output tokens supported or configured when known.
     */
    private final Integer maxOutputTokens;

    /**
     * Extra metadata fields that do not belong in the stable core model yet.
     */
    private final Map<String, Object> additionalProperties;

    @Builder
    public AiModelMetadata(String id,
                           String provider,
                           String modelName,
                           String displayName,
                           Integer contextWindowTokens,
                           Integer maxOutputTokens,
                           @Singular("additionalProperty") Map<String, Object> additionalProperties) {
        this.id = requireText(id, "AI model metadata id must not be blank");
        this.provider = normalize(provider);
        this.modelName = normalize(modelName);
        this.displayName = normalize(displayName);
        this.contextWindowTokens = contextWindowTokens;
        this.maxOutputTokens = maxOutputTokens;
        this.additionalProperties = new LinkedHashMap<>();
        if (additionalProperties != null) {
            this.additionalProperties.putAll(additionalProperties);
        }
    }

    /**
     * Creates minimal metadata when only a registry id is available.
     */
    public static AiModelMetadata of(String id) {
        return builder().id(id).build();
    }

    private static String requireText(String value, String message) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

}
