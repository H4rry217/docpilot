package io.docpilot.config;

import io.docpilot.ai.AiChatModel;
import io.docpilot.ai.AiModelRegistry;
import io.docpilot.ai.openai.OpenAiCompatibleChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiApplicationConfig {

    private static final String OPENAI_COMPATIBLE_PROVIDER = "openai-compatible";

    @Bean
    public AiModelRegistry aiModelRegistry(AiProperties properties) {
        List<AiChatModel> models = new ArrayList<>();
        for (Map.Entry<String, AiProperties.Model> entry : properties.getModels().entrySet()) {
            String modelId = entry.getKey();
            AiProperties.Model modelProperties = entry.getValue();
            if (modelProperties == null || !modelProperties.isEnabled()) {
                continue;
            }
            models.add(toModel(modelId, modelProperties));
        }
        return new AiModelRegistry(properties.getDefaultModelId(), models);
    }

    private AiChatModel toModel(String modelId, AiProperties.Model properties) {
        String provider = properties.getProvider();
        if (provider == null || provider.isBlank()) {
            provider = OPENAI_COMPATIBLE_PROVIDER;
        }
        if (!OPENAI_COMPATIBLE_PROVIDER.equals(provider)) {
            throw new IllegalArgumentException("Unsupported AI model provider for " + modelId + ": " + provider);
        }
        return new OpenAiCompatibleChatModel(
                modelId,
                properties.getBaseUrl(),
                properties.getApiKey(),
                properties.getModel(),
                properties.getTimeout()
        );
    }

}
