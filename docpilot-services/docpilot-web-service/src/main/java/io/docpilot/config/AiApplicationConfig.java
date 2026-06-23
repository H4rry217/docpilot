package io.docpilot.config;

import io.docpilot.ai.AiChatModel;
import io.docpilot.ai.AiEmbeddingModel;
import io.docpilot.ai.AiEmbeddingRegistry;
import io.docpilot.ai.AiModelMetadata;
import io.docpilot.ai.AiModelRegistry;
import io.docpilot.ai.provider.openai.OpenAiCompatibleChatModel;
import io.docpilot.ai.provider.openai.OpenAiCompatibleEmbeddingModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(AiConfig.class)
public class AiApplicationConfig {

    private static final String OPENAI_COMPATIBLE_PROVIDER = "openai-compatible";

    @Bean
    public AiModelRegistry aiModelRegistry(AiConfig config) {
        List<AiChatModel> models = new ArrayList<>();
        for (Map.Entry<String, AiConfig.ChatModel> entry : config.getModels().entrySet()) {
            String modelId = entry.getKey();
            AiConfig.ChatModel modelConfig = entry.getValue();
            if (modelConfig == null || !modelConfig.isEnabled()) {
                continue;
            }
            models.add(toModel(modelId, modelConfig));
        }
        return new AiModelRegistry(config.getDefaultModelId(), models);
    }

    @Bean
    public AiEmbeddingRegistry aiEmbeddingRegistry(AiConfig config) {
        List<AiEmbeddingModel> models = new ArrayList<>();
        for (Map.Entry<String, AiConfig.EmbeddingModel> entry : config.getEmbeddings().entrySet()) {
            String modelId = entry.getKey();
            AiConfig.EmbeddingModel modelConfig = entry.getValue();
            if (modelConfig == null || !modelConfig.isEnabled()) {
                continue;
            }
            models.add(toEmbeddingModel(modelId, modelConfig));
        }
        return new AiEmbeddingRegistry(config.getDefaultEmbeddingModelId(), models);
    }

    private AiChatModel toModel(String modelId, AiConfig.ChatModel config) {
        String provider = config.getProvider();
        if (provider == null || provider.isBlank()) {
            provider = OPENAI_COMPATIBLE_PROVIDER;
        }
        if (!OPENAI_COMPATIBLE_PROVIDER.equals(provider)) {
            throw new IllegalArgumentException("Unsupported AI model provider for " + modelId + ": " + provider);
        }
        return new OpenAiCompatibleChatModel(
                toChatMetadata(modelId, provider, config),
                config.getBaseUrl(),
                config.getApiKey(),
                config.getModel(),
                config.getTimeout(),
                config.getHeaders()
        );
    }

    private AiEmbeddingModel toEmbeddingModel(String modelId, AiConfig.EmbeddingModel config) {
        String provider = config.getProvider();
        if (provider == null || provider.isBlank()) {
            provider = OPENAI_COMPATIBLE_PROVIDER;
        }
        if (!OPENAI_COMPATIBLE_PROVIDER.equals(provider)) {
            throw new IllegalArgumentException("Unsupported AI embedding provider for " + modelId + ": " + provider);
        }
        return new OpenAiCompatibleEmbeddingModel(
                toEmbeddingMetadata(modelId, provider, config),
                config.getBaseUrl(),
                config.getApiKey(),
                config.getModel(),
                requireEmbeddingDimensions(modelId, config.getDimensions()),
                config.getTimeout(),
                config.getHeaders()
        );
    }

    private AiModelMetadata toChatMetadata(String modelId, String provider, AiConfig.ChatModel config) {
        var builder = AiModelMetadata.builder()
                .id(modelId)
                .provider(provider)
                .modelName(config.getModel())
                .displayName(config.getDisplayName())
                .contextWindowTokens(config.getContextWindowTokens())
                .maxOutputTokens(config.getMaxOutputTokens());
        if (config.getMetadata() != null) {
            config.getMetadata().forEach(builder::additionalProperty);
        }
        return builder.build();
    }

    private AiModelMetadata toEmbeddingMetadata(String modelId, String provider, AiConfig.EmbeddingModel config) {
        var builder = AiModelMetadata.builder()
                .id(modelId)
                .provider(provider)
                .modelName(config.getModel())
                .displayName(config.getDisplayName());
        if (config.getMetadata() != null) {
            config.getMetadata().forEach(builder::additionalProperty);
        }
        if (config.getDimensions() != null) {
            builder.additionalProperty("dimensions", config.getDimensions());
        }
        return builder.build();
    }

    private Integer requireEmbeddingDimensions(String modelId, Integer dimensions) {
        if (dimensions == null || dimensions <= 0) {
            throw new IllegalArgumentException("AI embedding model dimensions must be configured for " + modelId);
        }
        return dimensions;
    }

}
