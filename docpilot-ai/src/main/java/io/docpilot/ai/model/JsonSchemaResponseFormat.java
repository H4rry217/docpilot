package io.docpilot.ai.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Structured output response format backed by a JSON Schema.
 *
 * <p>This class models the provider-neutral intent. Individual adapters decide how to serialize it
 * into their wire format, such as OpenAI-compatible {@code response_format}.
 */
public class JsonSchemaResponseFormat implements ChatResponseFormat {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Schema name sent to providers that require named structured output.
     */
    @Getter
    private final String name;

    /**
     * Whether providers should enforce the schema strictly when supported.
     */
    @Getter
    private final Boolean strict;

    /**
     * Reusable schema value before provider-specific wire serialization.
     */
    private final JsonSchema schema;

    /**
     * Creates a named response format.
     */
    @Builder
    public JsonSchemaResponseFormat(String name, Boolean strict, JsonSchema schema) {
        this.name = requireText(name, "LLM JSON schema name must not be blank.");
        this.strict = strict == null ? true : strict;
        this.schema = schema == null ? JsonSchema.builder().build() : schema;
    }

    /**
     * Creates a strict empty-object JSON schema response format.
     */
    public static JsonSchemaResponseFormat jsonSchema(String name) {
        return builder()
                .name(name)
                .build();
    }

    /**
     * Creates a strict response format around an existing {@link JsonSchema}.
     */
    public static JsonSchemaResponseFormat of(String name, JsonSchema jsonSchema) {
        return builder()
                .name(name)
                .schema(Objects.requireNonNull(jsonSchema, "JSON schema must not be null."))
                .build();
    }

    /**
     * Returns the JSON-serializable schema body.
     */
    public Object getSchema() {
        return schema.toSchema();
    }

    /**
     * Returns the reusable schema object.
     */
    public JsonSchema jsonSchema() {
        return schema;
    }

    /**
     * Serializes this response format as JSON for diagnostics and tests.
     */
    public String toJsonString() {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("name", name);
            payload.put("strict", strict);
            payload.put("schema", getSchema());
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize LLM JSON schema response format.", exception);
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

}
