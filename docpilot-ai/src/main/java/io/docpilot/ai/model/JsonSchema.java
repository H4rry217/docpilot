package io.docpilot.ai.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * JSON Schema value used by AI response formats and future tool contracts.
 *
 * <p>The builder focuses on object-shaped schemas because that is the common structured-output
 * path for document editing tasks, while {@link #raw(Object)} keeps the module flexible for schema
 * features the builder does not expose yet.
 */
public class JsonSchema {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Object schema;

    /**
     * Wraps a JSON-serializable schema object.
     */
    public JsonSchema(Object schema) {
        this.schema = Objects.requireNonNull(schema, "LLM JSON schema body must not be null.");
    }

    /**
     * Wraps a hand-written top-level schema.
     */
    public static JsonSchema raw(Object schema) {
        return new JsonSchema(schema);
    }

    /**
     * Starts a structured output schema builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a string property.
     */
    public static StringProp stringProp(String name) {
        return new StringProp(name);
    }

    /**
     * Creates a number property.
     */
    public static NumberProp numberProp(String name) {
        return new NumberProp(name);
    }

    /**
     * Creates an integer property.
     */
    public static IntegerProp integerProp(String name) {
        return new IntegerProp(name);
    }

    /**
     * Creates a boolean property.
     */
    public static BooleanProp booleanProp(String name) {
        return new BooleanProp(name);
    }

    /**
     * Creates a null-valued property.
     */
    public static NullProp nullProp(String name) {
        return new NullProp(name);
    }

    /**
     * Creates an object property.
     */
    public static ObjectProp objectProp(String name) {
        return new ObjectProp(name);
    }

    /**
     * Creates an array property with an item schema.
     */
    public static ArrayProp arrayProp(String name, Schema items) {
        return new ArrayProp(name).items(items);
    }

    /**
     * Creates an array property whose item schema can be supplied later.
     */
    public static ArrayProp arrayProp(String name) {
        return new ArrayProp(name);
    }

    /**
     * Creates an enum property.
     */
    public static EnumProp enumProp(String name, List<?> values) {
        return new EnumProp(name, values);
    }

    /**
     * Creates an enum property from varargs values.
     */
    public static EnumProp enumProp(String name, Object... values) {
        return enumProp(name, Arrays.asList(values));
    }

    /**
     * String item schema for arrays.
     */
    public static Schema stringItem() {
        return typedSchema("string");
    }

    /**
     * Number item schema for arrays.
     */
    public static Schema numberItem() {
        return typedSchema("number");
    }

    /**
     * Integer item schema for arrays.
     */
    public static Schema integerItem() {
        return typedSchema("integer");
    }

    /**
     * Boolean item schema for arrays.
     */
    public static Schema booleanItem() {
        return typedSchema("boolean");
    }

    /**
     * Null item schema for arrays.
     */
    public static Schema nullItem() {
        return typedSchema("null");
    }

    /**
     * Object item schema for arrays.
     */
    public static ObjectProp objectItem() {
        return objectProp("_item");
    }

    /**
     * Array item schema for nested arrays.
     */
    public static ArrayProp arrayItem(Schema items) {
        return arrayProp("_item", items);
    }

    /**
     * Wraps a hand-written schema fragment.
     */
    public static Schema rawSchema(Object schema) {
        return () -> Objects.requireNonNull(schema, "JSON schema must not be null.");
    }

    /**
     * Returns the JSON-serializable schema object.
     */
    public Object toSchema() {
        return schema;
    }

    /**
     * Serializes this schema as JSON for diagnostics and tests.
     */
    public String toJsonString() {
        try {
            return OBJECT_MAPPER.writeValueAsString(schema);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize LLM JSON schema.", exception);
        }
    }

    private static Schema typedSchema(String type) {
        String schemaType = requireText(type, "JSON schema type must not be blank.");
        return () -> {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", schemaType);
            return schema;
        };
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    /**
     * A value that can render itself as a JSON Schema fragment.
     */
    public interface Schema {

        /**
         * Builds the JSON-serializable schema fragment.
         */
        Object toSchema();

    }

    /**
     * Base class for named schema properties.
     */
    @Getter
    public abstract static class Prop<T extends Prop<T>> implements Schema {

        /**
         * Property name.
         */
        private final String name;

        /**
         * Whether this property is listed in required.
         */
        private boolean required = true;

        /**
         * Optional human-readable property description.
         */
        private String description;

        protected Prop(String name) {
            this.name = requireText(name, "JSON schema property name must not be blank.");
        }

        /**
         * Adds a property description.
         */
        public T description(String description) {
            this.description = description;
            return self();
        }

        /**
         * Sets whether this property is required.
         */
        public T required(boolean required) {
            this.required = required;
            return self();
        }

        /**
         * Marks this property as optional.
         */
        public T optional() {
            return required(false);
        }

        /**
         * Builds this property's schema, including its description when present.
         */
        @Override
        public Map<String, Object> toSchema() {
            Map<String, Object> schema = buildSchema();
            if (description != null && !description.isBlank()) {
                schema.put("description", description);
            }
            return schema;
        }

        protected abstract T self();

        protected abstract Map<String, Object> buildSchema();

    }

    /**
     * Base class for simple typed properties.
     */
    public abstract static class TypedProp<T extends TypedProp<T>> extends Prop<T> {

        /**
         * JSON Schema type value.
         */
        private final String schemaType;

        protected TypedProp(String name, String schemaType) {
            super(name);
            this.schemaType = requireText(schemaType, "JSON schema type must not be blank.");
        }

        @Override
        protected Map<String, Object> buildSchema() {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", schemaType);
            return schema;
        }

    }

    /**
     * String property.
     */
    public static class StringProp extends TypedProp<StringProp> {

        private StringProp(String name) {
            super(name, "string");
        }

        @Override
        protected StringProp self() {
            return this;
        }

    }

    /**
     * Number property.
     */
    public static class NumberProp extends TypedProp<NumberProp> {

        private NumberProp(String name) {
            super(name, "number");
        }

        @Override
        protected NumberProp self() {
            return this;
        }

    }

    /**
     * Integer property.
     */
    public static class IntegerProp extends TypedProp<IntegerProp> {

        private IntegerProp(String name) {
            super(name, "integer");
        }

        @Override
        protected IntegerProp self() {
            return this;
        }

    }

    /**
     * Boolean property.
     */
    public static class BooleanProp extends TypedProp<BooleanProp> {

        private BooleanProp(String name) {
            super(name, "boolean");
        }

        @Override
        protected BooleanProp self() {
            return this;
        }

    }

    /**
     * Null property.
     */
    public static class NullProp extends TypedProp<NullProp> {

        private NullProp(String name) {
            super(name, "null");
        }

        @Override
        protected NullProp self() {
            return this;
        }

    }

    /**
     * Enum property.
     */
    public static class EnumProp extends Prop<EnumProp> {

        /**
         * Allowed values.
         */
        private final List<?> values;

        private EnumProp(String name, List<?> values) {
            super(name);
            this.values = List.copyOf(Objects.requireNonNull(values, "JSON schema enum values must not be null."));
        }

        @Override
        protected EnumProp self() {
            return this;
        }

        @Override
        protected Map<String, Object> buildSchema() {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("enum", values);
            return schema;
        }

    }

    /**
     * Object property with nested fields.
     */
    public static class ObjectProp extends Prop<ObjectProp> {

        /**
         * Nested property schemas.
         */
        private final Map<String, Object> properties = new LinkedHashMap<>();

        /**
         * Required nested property names.
         */
        private final List<String> required = new ArrayList<>();

        /**
         * Whether unknown nested fields are allowed.
         */
        private Boolean additionalProperties = false;

        private ObjectProp(String name) {
            super(name);
        }

        /**
         * Adds a nested property.
         */
        public ObjectProp prop(Prop<?> prop) {
            Prop<?> property = Objects.requireNonNull(prop, "JSON schema property must not be null.");
            properties.put(property.getName(), property.toSchema());
            if (property.isRequired()) {
                required.add(property.getName());
            }
            return this;
        }

        /**
         * Sets additionalProperties for the nested object.
         */
        public ObjectProp additionalProperties(Boolean additionalProperties) {
            this.additionalProperties = additionalProperties;
            return this;
        }

        @Override
        protected ObjectProp self() {
            return this;
        }

        @Override
        protected Map<String, Object> buildSchema() {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", properties);
            schema.put("required", required);
            if (additionalProperties != null) {
                schema.put("additionalProperties", additionalProperties);
            }
            return schema;
        }

    }

    /**
     * Array property.
     */
    public static class ArrayProp extends Prop<ArrayProp> {

        /**
         * Item schema for the array.
         */
        private Schema items;

        private ArrayProp(String name) {
            super(name);
        }

        /**
         * Sets the item schema.
         */
        public ArrayProp items(Schema items) {
            this.items = Objects.requireNonNull(items, "JSON schema array items must not be null.");
            return this;
        }

        @Override
        protected ArrayProp self() {
            return this;
        }

        @Override
        protected Map<String, Object> buildSchema() {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "array");
            schema.put("items", Objects.requireNonNull(items, "JSON schema array items must not be null.").toSchema());
            return schema;
        }

    }

    /**
     * Builder for a top-level JSON schema object.
     */
    public static class Builder {

        /**
         * Optional raw top-level schema.
         */
        private Object schema;

        /**
         * Whether unknown top-level fields are allowed.
         */
        private Boolean additionalProperties = false;

        /**
         * Top-level property schemas.
         */
        private final Map<String, Object> properties = new LinkedHashMap<>();

        /**
         * Required top-level property names.
         */
        private final List<String> required = new ArrayList<>();

        /**
         * Uses a raw top-level schema instead of properties added through this builder.
         */
        public Builder schema(Object schema) {
            this.schema = Objects.requireNonNull(schema, "LLM JSON schema body must not be null.");
            return this;
        }

        /**
         * Sets additionalProperties for the top-level object.
         */
        public Builder additionalProperties(Boolean additionalProperties) {
            this.additionalProperties = additionalProperties;
            return this;
        }

        /**
         * Adds a top-level property.
         */
        public Builder prop(Prop<?> prop) {
            Prop<?> property = Objects.requireNonNull(prop, "JSON schema property must not be null.");
            properties.put(property.getName(), property.toSchema());
            if (property.isRequired()) {
                required.add(property.getName());
            }
            return this;
        }

        /**
         * Adds multiple top-level properties.
         */
        public Builder props(Prop<?>... props) {
            for (Prop<?> prop : props) {
                prop(prop);
            }
            return this;
        }

        /**
         * Builds the JSON schema value.
         */
        public JsonSchema build() {
            Object builtSchema = schema == null ? buildObjectSchema() : schema;
            return new JsonSchema(builtSchema);
        }

        private Map<String, Object> buildObjectSchema() {
            Map<String, Object> objectSchema = new LinkedHashMap<>();
            objectSchema.put("type", "object");
            objectSchema.put("properties", properties);
            objectSchema.put("required", required);
            if (additionalProperties != null) {
                objectSchema.put("additionalProperties", additionalProperties);
            }
            return objectSchema;
        }

    }

}
