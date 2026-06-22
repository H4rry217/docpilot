package io.docpilot.common.json;

import io.docpilot.common.enums.BaseEnum;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

public class JacksonBaseEnumSerializer extends ValueSerializer<BaseEnum> {

    @Override
    public void serialize(BaseEnum baseEnum,
                          JsonGenerator jsonGenerator,
                          SerializationContext serializationContext) throws JacksonException {
        serializationContext.writeValue(jsonGenerator, baseEnum.getValue());
    }

}
