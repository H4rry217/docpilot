package io.docpilot.config;

import io.docpilot.ai.AiChatModel;
import io.docpilot.ai.AiModelMetadata;
import io.docpilot.ai.AiModelRegistry;
import io.docpilot.ai.provider.openai.OpenAiCompatibleChatModel;
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
        for (Map.Entry<String, AiConfig.Model> entry : config.getModels().entrySet()) {
            String modelId = entry.getKey();
            AiConfig.Model modelConfig = entry.getValue();
            if (modelConfig == null || !modelConfig.isEnabled()) {
                continue;
            }
            models.add(toModel(modelId, modelConfig));
        }
        return new AiModelRegistry(config.getDefaultModelId(), models);
    }

    private AiChatModel toModel(String modelId, AiConfig.Model config) {
        String provider = config.getProvider();
        if (provider == null || provider.isBlank()) {
            provider = OPENAI_COMPATIBLE_PROVIDER;
        }
        if (!OPENAI_COMPATIBLE_PROVIDER.equals(provider)) {
            throw new IllegalArgumentException("Unsupported AI model provider for " + modelId + ": " + provider);
        }
        return new OpenAiCompatibleChatModel(
                toMetadata(modelId, provider, config),
                config.getBaseUrl(),
                config.getApiKey(),
                config.getModel(),
                config.getTimeout()
        );
    }

    private AiModelMetadata toMetadata(String modelId, String provider, AiConfig.Model config) {
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

}
