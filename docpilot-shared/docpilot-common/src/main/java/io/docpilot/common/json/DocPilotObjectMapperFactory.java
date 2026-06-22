package io.docpilot.common.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.docpilot.common.enums.BaseEnum;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

public final class DocPilotObjectMapperFactory {

    public static ObjectMapper create() {
        return customize(JsonMapper.builder()).build();
    }

    public static JsonMapper.Builder customize(JsonMapper.Builder builder) {
        return builder
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .changeDefaultPropertyInclusion(value -> JsonInclude.Value.construct(
                        JsonInclude.Include.NON_NULL,
                        JsonInclude.Include.NON_NULL))
                .addModule(createDocPilotModule());
    }

    private static SimpleModule createDocPilotModule() {
        SimpleModule module = new SimpleModule();
        module.addSerializer(BaseEnum.class, new JacksonBaseEnumSerializer());
        module.addDeserializer(Enum.class, new JacksonBaseEnumDeserializer());
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(LocalDateTime.class, new LocalDateTimeEpochSecondSerializer());
        module.addDeserializer(LocalDateTime.class, new LocalDateTimeEpochSecondDeserializer());
        return module;
    }

}
