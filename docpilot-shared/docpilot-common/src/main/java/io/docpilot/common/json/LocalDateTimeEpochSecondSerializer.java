package io.docpilot.common.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import io.docpilot.common.constant.DocPilotConstants;

import java.io.IOException;
import java.time.LocalDateTime;

public class LocalDateTimeEpochSecondSerializer extends JsonSerializer<LocalDateTime> {

    @Override
    public void serialize(LocalDateTime localDateTime,
                          JsonGenerator jsonGenerator,
                          SerializerProvider serializerProvider) throws IOException {
        jsonGenerator.writeNumber(localDateTime.atZone(DocPilotConstants.DEFAULT_ZONE_ID).toEpochSecond());
    }

}
