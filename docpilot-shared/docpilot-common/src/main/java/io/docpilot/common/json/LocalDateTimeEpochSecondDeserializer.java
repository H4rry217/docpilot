package io.docpilot.common.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import io.docpilot.common.constant.DocPilotConstants;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class LocalDateTimeEpochSecondDeserializer extends JsonDeserializer<LocalDateTime> {

    @Override
    public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.getCurrentToken().isNumeric()) {
            return LocalDateTime.ofInstant(Instant.ofEpochSecond(parser.getLongValue()), ZoneId.systemDefault());
        }
        return LocalDateTime.parse(parser.getText(), DocPilotConstants.TIME_FORMATTER);
    }

}
