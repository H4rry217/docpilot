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
    private Map<String, Model> models = new LinkedHashMap<>();

    @Getter
    @Setter
    public static class Model {

        private boolean enabled = true;
        private String provider = "openai-compatible";
        private String baseUrl;
        private String apiKey;
        private String model;
        private Duration timeout = Duration.ofSeconds(60);

    }

}
