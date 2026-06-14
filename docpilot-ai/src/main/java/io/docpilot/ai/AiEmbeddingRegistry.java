package io.docpilot.ai;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable registry of configured embedding models.
 */
public class AiEmbeddingRegistry {

    private final String defaultModelId;
    private final Map<String, AiEmbeddingModel> models;

    public AiEmbeddingRegistry(String defaultModelId, Collection<? extends AiEmbeddingModel> models) {
        this.defaultModelId = normalize(defaultModelId);
        Map<String, AiEmbeddingModel> registeredModels = new LinkedHashMap<>();
        if (models != null) {
            for (AiEmbeddingModel model : models) {
                Objects.requireNonNull(model, "model must not be null");
                String modelId = normalize(model.id());
                if (modelId == null) {
                    throw new IllegalArgumentException("AI embedding model id must not be blank");
                }
                registeredModels.put(modelId, model);
            }
        }
        this.models = Collections.unmodifiableMap(registeredModels);
    }

    public AiEmbeddingModel resolve(String modelId) {
        String resolvedModelId = normalize(modelId);
        if (resolvedModelId == null) {
            resolvedModelId = defaultModelId;
        }
        if (resolvedModelId == null) {
            throw new IllegalArgumentException("AI embedding model id is required and no default model is configured");
        }
        AiEmbeddingModel model = models.get(resolvedModelId);
        if (model == null) {
            throw new IllegalArgumentException("AI embedding model is not configured or enabled: " + resolvedModelId);
        }
        return model;
    }

    public Optional<AiEmbeddingModel> find(String modelId) {
        String resolvedModelId = normalize(modelId);
        if (resolvedModelId == null) {
            resolvedModelId = defaultModelId;
        }
        return Optional.ofNullable(resolvedModelId == null ? null : models.get(resolvedModelId));
    }

    public Set<String> modelIds() {
        return models.keySet();
    }

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
