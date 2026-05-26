package io.docpilot.common.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import io.docpilot.common.enums.BaseEnum;

import java.io.IOException;

public class JacksonBaseEnumDeserializer extends JsonDeserializer<Enum<?>> implements ContextualDeserializer {

    private final Class<? extends Enum<?>> enumType;

    public JacksonBaseEnumDeserializer() {
        this(null);
    }

    private JacksonBaseEnumDeserializer(Class<? extends Enum<?>> enumType) {
        this.enumType = enumType;
    }

    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext context,
                                                BeanProperty property) throws JsonMappingException {
        Class<?> rawClass = context.getContextualType() == null ? null : context.getContextualType().getRawClass();
        if (rawClass == null && property != null) {
            rawClass = property.getType().getRawClass();
        }
        if (rawClass != null && rawClass.isEnum()) {
            @SuppressWarnings("unchecked")
            Class<? extends Enum<?>> resolvedEnumType = (Class<? extends Enum<?>>) rawClass;
            return new JacksonBaseEnumDeserializer(resolvedEnumType);
        }
        return this;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Enum<?> deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (enumType == null) {
            return (Enum<?>) context.handleUnexpectedToken(Enum.class, parser);
        }

        String value = parser.getText();
        if (BaseEnum.class.isAssignableFrom(enumType)) {
            for (Enum<?> candidate : enumType.getEnumConstants()) {
                Object enumValue = ((BaseEnum<?, ?>) candidate).getValue();
                if (enumValue != null && enumValue.toString().equals(value)) {
                    return candidate;
                }
            }
            return (Enum<?>) context.handleWeirdStringValue(enumType, value, "No matching BaseEnum value");
        }

        Class rawEnumType = enumType;
        return Enum.valueOf(rawEnumType, value);
    }

}
