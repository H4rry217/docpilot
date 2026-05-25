package io.docpilot.ai;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class AiModelRegistry {

    private final String defaultModelId;
    private final Map<String, AiChatModel> models;

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

    public Optional<AiChatModel> find(String modelId) {
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
