package io.docpilot.ai.model;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonSchemaTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildsEmptyObjectSchema() throws IOException {
        JsonSchema schema = JsonSchema.builder().build();

        JsonNode root = read(schema);

        assertThat(root.path("type").asText()).isEqualTo("object");
        assertThat(root.path("properties").isObject()).isTrue();
        assertThat(root.path("required").isArray()).isTrue();
        assertThat(root.path("additionalProperties").asBoolean()).isFalse();
    }

    @Test
    void buildsAllPrimitivePropertyTypes() throws IOException {
        JsonSchema schema = JsonSchema.builder()
                .props(
                        JsonSchema.stringProp("text").description("A text field"),
                        JsonSchema.numberProp("amount"),
                        JsonSchema.integerProp("count"),
                        JsonSchema.booleanProp("enabled"),
                        JsonSchema.nullProp("deleted_at").optional()
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
        JsonSchema schema = JsonSchema.builder()
                .prop(JsonSchema.enumProp("status", List.of("draft", "paid", "cancelled"))
                        .description("Order status"))
                .build();

        JsonNode status = schemaNode(schema).path("properties").path("status");

        assertThat(status.path("enum")).extracting(JsonNode::asText)
                .containsExactly("draft", "paid", "cancelled");
        assertThat(status.path("description").asText()).isEqualTo("Order status");
    }

    @Test
    void buildsNestedObjectProperty() throws IOException {
        JsonSchema schema = JsonSchema.builder()
                .prop(JsonSchema.objectProp("customer")
                        .description("Customer profile")
                        .prop(JsonSchema.stringProp("name"))
                        .prop(JsonSchema.integerProp("age").optional())
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
        JsonSchema schema = JsonSchema.builder()
                .props(
                        JsonSchema.arrayProp("strings", JsonSchema.stringItem()),
                        JsonSchema.arrayProp("numbers", JsonSchema.numberItem()),
                        JsonSchema.arrayProp("integers", JsonSchema.integerItem()),
                        JsonSchema.arrayProp("booleans", JsonSchema.booleanItem()),
                        JsonSchema.arrayProp("nulls", JsonSchema.nullItem()).optional()
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
        JsonSchema schema = JsonSchema.builder()
                .prop(JsonSchema.arrayProp("items", JsonSchema.objectItem()
                        .prop(JsonSchema.stringProp("sku"))
                        .prop(JsonSchema.numberProp("price"))
                        .prop(JsonSchema.integerProp("quantity"))))
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
    void buildsArrayItemCountLimits() throws IOException {
        JsonSchema schema = JsonSchema.builder()
                .prop(JsonSchema.arrayProp("candidates", JsonSchema.objectItem()
                                .prop(JsonSchema.stringProp("markdown")))
                        .minItems(1)
                        .maxItems(5))
                .build();

        JsonNode candidates = schemaNode(schema).path("properties").path("candidates");

        assertThat(candidates.path("type").asText()).isEqualTo("array");
        assertThat(candidates.path("minItems").asInt()).isEqualTo(1);
        assertThat(candidates.path("maxItems").asInt()).isEqualTo(5);
        assertThat(candidates.path("items").path("properties").path("markdown").path("type").asText())
                .isEqualTo("string");
    }

    @Test
    void buildsNestedArrayItems() throws IOException {
        JsonSchema schema = JsonSchema.builder()
                .prop(JsonSchema.arrayProp("matrix",
                        JsonSchema.arrayItem(JsonSchema.numberItem())))
                .build();

        JsonNode matrix = schemaNode(schema).path("properties").path("matrix");

        assertThat(matrix.path("type").asText()).isEqualTo("array");
        assertThat(matrix.path("items").path("type").asText()).isEqualTo("array");
        assertThat(matrix.path("items").path("items").path("type").asText()).isEqualTo("number");
    }

    @Test
    void buildsRawRootSchema() throws IOException {
        JsonSchema schema = JsonSchema.builder()
                .schema(Map.of(
                        "type", "object",
                        "properties", Map.of("answer", Map.of("type", "string")),
                        "required", List.of("answer"),
                        "additionalProperties", false
                ))
                .build();

        JsonNode root = read(schema);

        assertThat(root.path("properties")
                .path("answer").path("type").asText()).isEqualTo("string");
    }

    @Test
    void buildsRawPropertySchema() throws IOException {
        JsonSchema schema = JsonSchema.builder()
                .prop(JsonSchema.arrayProp("events", JsonSchema.rawSchema(Map.of(
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
        JsonSchema schema = JsonSchema.builder()
                .additionalProperties(null)
                .prop(JsonSchema.objectProp("payload")
                        .additionalProperties(null)
                        .prop(JsonSchema.stringProp("value")))
                .build();

        JsonNode schemaNode = schemaNode(schema);
        JsonNode payload = schemaNode.path("properties").path("payload");

        assertThat(schemaNode.has("additionalProperties")).isFalse();
        assertThat(payload.has("additionalProperties")).isFalse();
    }

    @Test
    void canBeAttachedToChatRequest() {
        ChatRequest request = new ChatRequest();
        request.setModel("configured-model");
        request.setMessages(List.of(new ChatMessage("user", "Extract fields.")));
        request.setResponseFormat(JsonSchemaResponseFormat.of("invoice_fields", JsonSchema.builder()
                .prop(JsonSchema.stringProp("invoice_number"))
                .build()));

        assertThat(request.getResponseFormat()).isInstanceOf(JsonSchemaResponseFormat.class);
    }

    @Test
    void wrapsJsonSchemaAsResponseFormat() throws IOException {
        JsonSchemaResponseFormat responseFormat = JsonSchemaResponseFormat.of("invoice_fields", JsonSchema.builder()
                .prop(JsonSchema.stringProp("invoice_number"))
                .build());

        JsonNode root = objectMapper.readTree(responseFormat.toJsonString());

        assertThat(root.path("name").asText()).isEqualTo("invoice_fields");
        assertThat(root.path("strict").asBoolean()).isTrue();
        assertThat(root.path("schema").path("properties")
                .path("invoice_number").path("type").asText()).isEqualTo("string");
        assertThat(root.has("jsonSchema")).isFalse();
    }

    @Test
    void removesResponseFormatFromChatRequest() {
        ChatRequest request = new ChatRequest();
        request.setResponseFormat(JsonSchemaResponseFormat.of("invoice_fields", JsonSchema.builder()
                .prop(JsonSchema.stringProp("invoice_number"))
                .build()));

        request.setResponseFormat(null);

        assertThat(request.getResponseFormat()).isNull();
    }

    @Test
    void validatesRequiredBuilderFields() {
        assertThatThrownBy(() -> JsonSchemaResponseFormat.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name must not be blank");
    }

    @Test
    void validatesInvalidProperties() {
        assertThatThrownBy(() -> JsonSchema.stringProp(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("property name must not be blank");
        assertThatThrownBy(() -> JsonSchema.builder().prop(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("property must not be null");
        assertThatThrownBy(() -> JsonSchema.arrayProp("items").toSchema())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("array items must not be null");
        assertThatThrownBy(() -> JsonSchema.enumProp("status", (List<?>) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("enum values must not be null");
    }

    private JsonNode read(JsonSchema schema) throws IOException {
        return objectMapper.readTree(schema.toJsonString());
    }

    private JsonNode schemaNode(JsonSchema schema) throws IOException {
        return read(schema);
    }

    private void assertArrayItemType(JsonNode properties, String propertyName, String type) {
        JsonNode property = properties.path(propertyName);
        assertThat(property.path("type").asText()).isEqualTo("array");
        assertThat(property.path("items").path("type").asText()).isEqualTo(type);
    }

}
