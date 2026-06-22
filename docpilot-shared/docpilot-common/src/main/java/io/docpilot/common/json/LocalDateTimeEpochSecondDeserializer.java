package io.docpilot.common.json;

import io.docpilot.common.constant.DocPilotConstants;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class LocalDateTimeEpochSecondDeserializer extends ValueDeserializer<LocalDateTime> {

    @Override
    public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        if (parser.currentToken().isNumeric()) {
            return LocalDateTime.ofInstant(Instant.ofEpochSecond(parser.getLongValue()), ZoneId.systemDefault());
        }
        return LocalDateTime.parse(parser.getString(), DocPilotConstants.TIME_FORMATTER);
    }

}
