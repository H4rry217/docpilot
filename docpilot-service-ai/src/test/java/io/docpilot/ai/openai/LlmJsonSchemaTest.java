package io.docpilot.ai.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmJsonSchemaTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildsResponseFormatEnvelope() throws IOException {
        LlmJsonSchema schema = LlmJsonSchema.builder()
                .name("empty_object")
                .build();

        JsonNode root = read(schema);

        assertThat(root.path("type").asText()).isEqualTo("json_schema");
        assertThat(root.path("json_schema").path("name").asText()).isEqualTo("empty_object");
        assertThat(root.path("json_schema").path("strict").asBoolean()).isTrue();
        JsonNode schemaNode = root.path("json_schema").path("schema");
        assertThat(schemaNode.path("type").asText()).isEqualTo("object");
        assertThat(schemaNode.path("properties").isObject()).isTrue();
        assertThat(schemaNode.path("required").isArray()).isTrue();
        assertThat(schemaNode.path("additionalProperties").asBoolean()).isFalse();
    }

    @Test
    void buildsAllPrimitivePropertyTypes() throws IOException {
        LlmJsonSchema schema = LlmJsonSchema.builder()
                .name("primitive_fields")
                .props(
                        LlmJsonSchema.stringProp("text").description("A text field"),
                        LlmJsonSchema.numberProp("amount"),
                        LlmJsonSchema.integerProp("count"),
                        LlmJsonSchema.booleanProp("enabled"),
                        LlmJsonSchema.nullProp("deleted_at").optional()
                )
                .build();

        JsonNode schemaNode = schemaNode(schema);
        JsonNode properties = schemaNode.path("properties");

        assertThat(properties.path("text").path("type").asText()).isEqualTo("string");
        assertThat(properties.path("text").path("description").asText()).isEqualTo("A text field");
        assertThat(properties.path("amount").path("type").asText()).isEqualTo("number");
        assertThat(properties.path("count").path("type").asText()).isEqualTo("integer");
        assertThat(properties.path("enabled").path("type").asText()).isEqualTo("boolean");
        assertThat(properties.path("deleted_at").path("type").asText()).isEqualTo("null");
        assertThat(schemaNode.path("required")).extracting(JsonNode::asText)
                .containsExactly("text", "amount", "count", "enabled");
    }

    @Test
    void buildsEnumProperty() throws IOException {
        LlmJsonSchema schema = LlmJsonSchema.builder()
                .name("status_fields")
                .prop(LlmJsonSchema.enumProp("status", List.of("draft", "paid", "cancelled"))
                        .description("Order status"))
                .build();

        JsonNode status = schemaNode(schema).path("properties").path("status");

        assertThat(status.path("enum")).extracting(JsonNode::asText)
                .containsExactly("draft", "paid", "cancelled");
        assertThat(status.path("description").asText()).isEqualTo("Order status");
    }

    @Test
    void buildsNestedObjectProperty() throws IOException {
        LlmJsonSchema schema = LlmJsonSchema.builder()
                .name("customer_fields")
                .prop(LlmJsonSchema.objectProp("customer")
                        .description("Customer profile")
                        .prop(LlmJsonSchema.stringProp("name"))
                        .prop(LlmJsonSchema.integerProp("age").optional())
                        .additionalProperties(true))
                .build();

        JsonNode customer = schemaNode(schema).path("properties").path("customer");

        assertThat(customer.path("type").asText()).isEqualTo("object");
        assertThat(customer.path("description").asText()).isEqualTo("Customer profile");
        assertThat(customer.path("additionalProperties").asBoolean()).isTrue();
        assertThat(customer.path("properties").path("name").path("type").asText()).isEqualTo("string");
        assertThat(customer.path("properties").path("age").path("type").asText()).isEqualTo("integer");
        assertThat(customer.path("required")).extracting(JsonNode::asText)
                .containsExactly("name");
    }

    @Test
    void buildsArraysWithPrimitiveItems() throws IOException {
        LlmJsonSchema schema = LlmJsonSchema.builder()
                .name("array_fields")
                .props(
                        LlmJsonSchema.arrayProp("strings", LlmJsonSchema.stringItem()),
                        LlmJsonSchema.arrayProp("numbers", LlmJsonSchema.numberItem()),
                        LlmJsonSchema.arrayProp("integers", LlmJsonSchema.integerItem()),
                        LlmJsonSchema.arrayProp("booleans", LlmJsonSchema.booleanItem()),
                        LlmJsonSchema.arrayProp("nulls", LlmJsonSchema.nullItem()).optional()
                )
                .build();

        JsonNode properties = schemaNode(schema).path("properties");

        assertArrayItemType(properties, "strings", "string");
        assertArrayItemType(properties, "numbers", "number");
        assertArrayItemType(properties, "integers", "integer");
        assertArrayItemType(properties, "booleans", "boolean");
        assertArrayItemType(properties, "nulls", "null");
    }

    @Test
    void buildsArrayWithObjectItems() throws IOException {
        LlmJsonSchema schema = LlmJsonSchema.builder()
                .name("line_items")
                .prop(LlmJsonSchema.arrayProp("items", LlmJsonSchema.objectItem()
                        .prop(LlmJsonSchema.stringProp("sku"))
                        .prop(LlmJsonSchema.numberProp("price"))
                        .prop(LlmJsonSchema.integerProp("quantity"))))
                .build();

        JsonNode itemSchema = schemaNode(schema).path("properties").path("items").path("items");

        assertThat(itemSchema.path("type").asText()).isEqualTo("object");
        assertThat(itemSchema.path("properties").path("sku").path("type").asText()).isEqualTo("string");
        assertThat(itemSchema.path("properties").path("price").path("type").asText()).isEqualTo("number");
        assertThat(itemSchema.path("properties").path("quantity").path("type").asText()).isEqualTo("integer");
        assertThat(itemSchema.path("required")).extracting(JsonNode::asText)
                .containsExactly("sku", "price", "quantity");
    }

    @Test
    void buildsNestedArrayItems() throws IOException {
        LlmJsonSchema schema = LlmJsonSchema.builder()
                .name("matrix_fields")
                .prop(LlmJsonSchema.arrayProp("matrix",
                        LlmJsonSchema.arrayItem(LlmJsonSchema.numberItem())))
                .build();

        JsonNode matrix = schemaNode(schema).path("properties").path("matrix");

        assertThat(matrix.path("type").asText()).isEqualTo("array");
        assertThat(matrix.path("items").path("type").asText()).isEqualTo("array");
        assertThat(matrix.path("items").path("items").path("type").asText()).isEqualTo("number");
    }

    @Test
    void buildsRawRootSchema() throws IOException {
        LlmJsonSchema schema = LlmJsonSchema.builder()
                .name("raw_schema")
                .strict(false)
                .schema(Map.of(
                        "type", "object",
                        "properties", Map.of("answer", Map.of("type", "string")),
                        "required", List.of("answer"),
                        "additionalProperties", false
                ))
                .build();

        JsonNode root = read(schema);

        assertThat(root.path("json_schema").path("strict").asBoolean()).isFalse();
        assertThat(root.path("json_schema").path("schema").path("properties")
                .path("answer").path("type").asText()).isEqualTo("string");
    }

    @Test
    void buildsRawPropertySchema() throws IOException {
        LlmJsonSchema schema = LlmJsonSchema.builder()
                .name("raw_property")
                .prop(LlmJsonSchema.arrayProp("events", LlmJsonSchema.rawSchema(Map.of(
                        "type", "object",
                        "properties", Map.of("at", Map.of("type", "string")),
                        "required", List.of("at"),
                        "additionalProperties", false
                ))))
                .build();

        JsonNode eventItem = schemaNode(schema).path("properties").path("events").path("items");

        assertThat(eventItem.path("type").asText()).isEqualTo("object");
        assertThat(eventItem.path("properties").path("at").path("type").asText()).isEqualTo("string");
        assertThat(eventItem.path("required")).extracting(JsonNode::asText).containsExactly("at");
    }

    @Test
    void omitsAdditionalPropertiesWhenExplicitlyNull() throws IOException {
        LlmJsonSchema schema = LlmJsonSchema.builder()
                .name("open_object")
                .additionalProperties(null)
                .prop(LlmJsonSchema.objectProp("payload")
                        .additionalProperties(null)
                        .prop(LlmJsonSchema.stringProp("value")))
                .build();

        JsonNode schemaNode = schemaNode(schema);
        JsonNode payload = schemaNode.path("properties").path("payload");

        assertThat(schemaNode.has("additionalProperties")).isFalse();
        assertThat(payload.has("additionalProperties")).isFalse();
    }

    @Test
    void canBeAttachedToChatCompletionRequest() throws IOException {
        LlmChatRequest request = new LlmChatRequest();
        request.setModel("configured-model");
        request.setMessages(List.of(new OpenAiChatMessage("user", "Extract fields.")));
        request.setResponseFormat(LlmJsonSchema.builder()
                .name("invoice_fields")
                .prop(LlmJsonSchema.stringProp("invoice_number"))
                .build());

        JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(request.toPayload()));

        assertThat(root.path("response_format").path("type").asText()).isEqualTo("json_schema");
        assertThat(root.path("response_format").path("json_schema").path("name").asText()).isEqualTo("invoice_fields");
        assertThat(root.path("response_format").path("json_schema").path("schema").path("properties")
                .path("invoice_number").path("type").asText()).isEqualTo("string");
    }

    @Test
    void removesResponseFormatFromChatCompletionRequest() throws IOException {
        LlmChatRequest request = new LlmChatRequest();
        request.setResponseFormat(LlmJsonSchema.builder()
                .name("invoice_fields")
                .prop(LlmJsonSchema.stringProp("invoice_number"))
                .build());

        request.setResponseFormat(null);

        JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(request.toPayload()));
        assertThat(root.has("response_format")).isFalse();
        assertThat(request.getResponseFormat()).isNull();
    }

    @Test
    void validatesRequiredBuilderFields() {
        assertThatThrownBy(() -> LlmJsonSchema.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name must not be blank");
        assertThatThrownBy(() -> LlmJsonSchema.builder().name("schema").type(" ").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type must not be blank");
    }

    @Test
    void validatesInvalidProperties() {
        assertThatThrownBy(() -> LlmJsonSchema.stringProp(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("property name must not be blank");
        assertThatThrownBy(() -> LlmJsonSchema.builder().name("schema").prop(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("property must not be null");
        assertThatThrownBy(() -> LlmJsonSchema.arrayProp("items").toSchema())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("array items must not be null");
        assertThatThrownBy(() -> LlmJsonSchema.enumProp("status", (List<?>) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("enum values must not be null");
    }

    private JsonNode read(LlmJsonSchema schema) throws IOException {
        return objectMapper.readTree(schema.toJsonString());
    }

    private JsonNode schemaNode(LlmJsonSchema schema) throws IOException {
        return read(schema).path("json_schema").path("schema");
    }

    private void assertArrayItemType(JsonNode properties, String propertyName, String type) {
        JsonNode property = properties.path(propertyName);
        assertThat(property.path("type").asText()).isEqualTo("array");
        assertThat(property.path("items").path("type").asText()).isEqualTo(type);
    }

}
