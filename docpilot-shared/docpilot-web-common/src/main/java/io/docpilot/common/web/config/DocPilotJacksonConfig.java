package io.docpilot.common.web.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.docpilot.common.json.DocPilotObjectMapperFactory;
import io.docpilot.common.json.JsonUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@Configuration
public class DocPilotJacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper mapper = builder.createXmlMapper(false).build();
        DocPilotObjectMapperFactory.configure(mapper);
        JsonUtils.setObjectMapper(mapper);
        return mapper;
    }

}
