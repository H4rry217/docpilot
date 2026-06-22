package io.docpilot.common.web.config;

import tools.jackson.databind.ObjectMapper;
import io.docpilot.common.json.DocPilotObjectMapperFactory;
import io.docpilot.common.json.JsonUtils;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DocPilotJacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer docPilotJsonMapperBuilderCustomizer() {
        return builder -> DocPilotObjectMapperFactory.customize(builder);
    }

    @Bean
    public Object docPilotJsonUtilsObjectMapperInitializer(ObjectMapper objectMapper) {
        JsonUtils.setObjectMapper(objectMapper);
        return new Object();
    }

}
