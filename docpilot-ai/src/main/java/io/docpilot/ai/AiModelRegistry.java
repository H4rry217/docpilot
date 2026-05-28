package io.docpilot.ai;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable registry of configured AI chat models.
 *
 * <p>The registry centralizes default-model resolution so application services do not need to
 * duplicate fallback and enabled-model checks.
 */
public class AiModelRegistry {

    private final String defaultModelId;
    private final Map<String, AiChatModel> models;

    /**
     * Creates a registry from model instances keyed by their own {@link AiChatModel#id()} value.
     */
    public AiModelRegistry(String defaultModelId, Collection<? extends AiChatModel> models) {
        this.defaultModelId = normalize(defaultModelId);
        Map<String, AiChatModel> registeredModels = new LinkedHashMap<>();
        if (models != null) {
            for (AiChatModel model : models) {
                Objects.requireNonNull(model, "model must not be null");
                String modelId = normalize(model.id());
                if (modelId == null) {
                    throw new IllegalArgumentException("AI model id must not be blank");
                }
                registeredModels.put(modelId, model);
            }
        }
        this.models = Collections.unmodifiableMap(registeredModels);
    }

    /**
     * Resolves a model id or the configured default, throwing a clear error when unavailable.
     */
    public AiChatModel resolve(String modelId) {
        String resolvedModelId = normalize(modelId);
        if (resolvedModelId == null) {
            resolvedModelId = defaultModelId;
        }
        if (resolvedModelId == null) {
            throw new IllegalArgumentException("AI model id is required and no default model is configured");
        }
        AiChatModel model = models.get(resolvedModelId);
        if (model == null) {
            throw new IllegalArgumentException("AI model is not configured or enabled: " + resolvedModelId);
        }
        return model;
    }

    /**
     * Resolves a model id or default model without throwing when the model is absent.
     */
    public Optional<AiChatModel> find(String modelId) {
        String resolvedModelId = normalize(modelId);
        if (resolvedModelId == null) {
            resolvedModelId = defaultModelId;
        }
        return Optional.ofNullable(resolvedModelId == null ? null : models.get(resolvedModelId));
    }

    /**
     * Resolves metadata for a model id or the configured default.
     */
    public AiModelMetadata resolveMetadata(String modelId) {
        return resolve(modelId).metadata();
    }

    /**
     * Returns configured metadata in model registration order.
     */
    public Map<String, AiModelMetadata> metadataByModelId() {
        Map<String, AiModelMetadata> metadata = new LinkedHashMap<>();
        for (Map.Entry<String, AiChatModel> entry : models.entrySet()) {
            metadata.put(entry.getKey(), entry.getValue().metadata());
        }
        return Collections.unmodifiableMap(metadata);
    }

    /**
     * Returns model ids in registration order.
     */
    public Set<String> modelIds() {
        return models.keySet();
    }

    /**
     * Returns the default model id after blank-value normalization.
     */
    public String defaultModelId() {
        return defaultModelId;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

}
