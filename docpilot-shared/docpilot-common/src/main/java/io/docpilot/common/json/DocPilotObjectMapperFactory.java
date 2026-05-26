package io.docpilot.common.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.docpilot.common.enums.BaseEnum;

import java.time.LocalDateTime;

public final class DocPilotObjectMapperFactory {

    public static ObjectMapper create() {
        ObjectMapper mapper = new ObjectMapper();
        configure(mapper);
        return mapper;
    }

    public static ObjectMapper configure(ObjectMapper mapper) {
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.registerModule(new JavaTimeModule());

        SimpleModule module = new SimpleModule();
        module.addSerializer(BaseEnum.class, new JacksonBaseEnumSerializer());
        module.addDeserializer(Enum.class, new JacksonBaseEnumDeserializer());
        module.addSerializer(Long.class, new ToStringSerializer());
        module.addSerializer(LocalDateTime.class, new LocalDateTimeEpochSecondSerializer());
        module.addDeserializer(LocalDateTime.class, new LocalDateTimeEpochSecondDeserializer());
        mapper.registerModule(module);
        return mapper;
    }

}
