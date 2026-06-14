package io.docpilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "docpilot.ai")
@Getter
@Setter
public class AiConfig {

    private String defaultModelId = "default";
    private Map<String, ChatModel> models = new LinkedHashMap<>();
    private String defaultEmbeddingModelId = "default";
    private Map<String, EmbeddingModel> embeddings = new LinkedHashMap<>();

    @Getter
    @Setter
    public static class BaseModel {

        private boolean enabled = true;
        private String provider = "openai-compatible";
        private String baseUrl;
        private String apiKey;
        private String model;
        private String displayName;
        private Map<String, Object> metadata = new LinkedHashMap<>();
        private Duration timeout = Duration.ofSeconds(60);

    }

    @Getter
    @Setter
    public static class ChatModel extends BaseModel {

        private Integer contextWindowTokens;
        private Integer maxOutputTokens;

    }

    @Getter
    @Setter
    public static class EmbeddingModel extends BaseModel {

        private Integer dimensions;

    }

}
