package io.docpilot.common.json;

import java.time.LocalDateTime;
import java.time.ZoneId;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

public class LocalDateTimeEpochSecondSerializer extends ValueSerializer<LocalDateTime> {

    @Override
    public void serialize(LocalDateTime localDateTime,
                          JsonGenerator jsonGenerator,
                          SerializationContext serializationContext) throws JacksonException {
        jsonGenerator.writeNumber(localDateTime.atZone(ZoneId.systemDefault()).toEpochSecond());
    }

}
