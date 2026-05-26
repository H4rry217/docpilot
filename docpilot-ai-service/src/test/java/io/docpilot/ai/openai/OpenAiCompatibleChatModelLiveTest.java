package io.docpilot.ai.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Live smoke tests for manually verifying an OpenAI-compatible provider.
 *
 * Fill the constants below with your own provider configuration, then run:
 * mvn -pl docpilot-service-ai -Dtest=OpenAiCompatibleChatModelLiveTest test
 */
class OpenAiCompatibleChatModelLiveTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String BASE_URL = "qwen3.6-flash";
    private static final String API_KEY = "qwen3.6-flash";
    private static final String MODEL = "qwen3.6-flash";
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    @Test
    void chatsWithRealLlm() {
        assumeTrue(isConfigured(), "Fill BASE_URL, API_KEY, and MODEL before running this live test.");
        OpenAiCompatibleChatModel chatModel = createModel();
        LlmChatRequest request = new LlmChatRequest();
        request.setMessages(List.of(
                new OpenAiChatMessage("system", "You are a concise assistant."),
                new OpenAiChatMessage("user", "Answer in one short Chinese sentence: what is DocPilot?")
        ));
        request.setTemperature(0.2);

        OpenAiChatCompletionResponse response = chatModel.chat(request);

        assertThat(response.getChoices()).isNotEmpty();
        Object content = response.getChoices().getFirst().getMessage().getContent();
        assertThat(content).isNotNull();
        System.out.println("LLM response: " + content);
    }

    @Test
    void structuredOutputWithPrimitiveTypes() throws IOException {
        LlmJsonSchema schema = LlmJsonSchema.builder()
                .name("primitive_types")
                .props(
                        LlmJsonSchema.stringProp("title"),
                        LlmJsonSchema.numberProp("amount"),
                        LlmJsonSchema.integerProp("count"),
                        LlmJsonSchema.booleanProp("paid"),
                        LlmJsonSchema.nullProp("deleted_at")
                )
                .build();

        JsonNode output = requestStructuredOutput(schema, """
                Return title = Pilot invoice, amount = 128.50, count = 2,
                paid = true, deleted_at = null.
                """);

        assertThat(output.path("title").asText()).isEqualTo("Pilot invoice");
        assertThat(output.path("amount").asDouble()).isEqualTo(128.50);
        assertThat(output.path("count").asInt()).isEqualTo(2);
        assertBoolean(output, "paid", true);
        assertThat(output.path("deleted_at").isNull()).isTrue();
    }

    @Test
    void structuredOutputWithEnum() throws IOException {
        LlmJsonSchema schema = LlmJsonSchema.builder()
                .name("enum_type")
                .prop(LlmJsonSchema.enumProp("status", List.of("draft", "paid", "cancelled")))
                .build();

        JsonNode output = requestStructuredOutput(schema, "Return status = paid.");

        assertThat(output.path("status").asText()).isEqualTo("paid");
    }

    @Test
    void structuredOutputWithObject() throws IOException {
        LlmJsonSchema schema = LlmJsonSchema.builder()
                .name("object_type")
                .prop(LlmJsonSchema.objectProp("customer")
                        .prop(LlmJsonSchema.stringProp("name"))
                        .prop(LlmJsonSchema.integerProp("age"))
                        .prop(LlmJsonSchema.booleanProp("vip")))
                .build();

        JsonNode output = requestStructuredOutput(schema, """
                Return customer.name = Alice, customer.age = 32, customer.vip = false.
                """);

        JsonNode customer = output.path("customer");
        assertThat(customer.path("name").asText()).isEqualTo("Alice");
        assertThat(customer.path("age").asInt()).isEqualTo(32);
        assertBoolean(customer, "vip", false);
    }

    @Test
    void structuredOutputWithPrimitiveArrays() throws IOException {
        LlmJsonSchema schema = LlmJsonSchema.builder()
                .name("primitive_arrays")
                .props(
                        LlmJsonSchema.arrayProp("tags", LlmJsonSchema.stringItem()),
                        LlmJsonSchema.arrayProp("scores", LlmJsonSchema.numberItem()),
                        LlmJsonSchema.arrayProp("flags", LlmJsonSchema.booleanItem())
                )
                .build();

        JsonNode output = requestStructuredOutput(schema, """
                Return tags = urgent, finance; scores = 98.5, 87.0; flags = true, false.
                """);

        assertThat(output.path("tags")).extracting(JsonNode::asText).containsExactly("urgent", "finance");
        assertThat(output.path("scores")).extracting(JsonNode::asDouble).containsExactly(98.5, 87.0);
        assertThat(output.path("flags")).extracting(JsonNode::asBoolean).containsExactly(true, false);
    }

    @Test
    void structuredOutputWithObjectArray() throws IOException {
        LlmJsonSchema schema = LlmJsonSchema.builder()
                .name("object_array")
                .prop(LlmJsonSchema.arrayProp("items", LlmJsonSchema.objectItem()
                        .prop(LlmJsonSchema.stringProp("sku"))
                        .prop(LlmJsonSchema.integerProp("quantity"))
                        .prop(LlmJsonSchema.numberProp("price"))))
                .build();

        JsonNode output = requestStructuredOutput(schema, """
                Return items = [{sku: A-1, quantity: 1, price: 64.25}, {sku: B-2, quantity: 1, price: 64.25}].
                """);

        JsonNode items = output.path("items");
        assertThat(items.size()).isEqualTo(2);
        assertThat(items.get(0).path("sku").asText()).isEqualTo("A-1");
        assertThat(items.get(0).path("quantity").asInt()).isEqualTo(1);
        assertThat(items.get(0).path("price").asDouble()).isEqualTo(64.25);
        assertThat(items.get(1).path("sku").asText()).isEqualTo("B-2");
        assertThat(items.get(1).path("quantity").asInt()).isEqualTo(1);
        assertThat(items.get(1).path("price").asDouble()).isEqualTo(64.25);
    }

    @Test
    void structuredOutputWithNestedArray() throws IOException {
        LlmJsonSchema schema = LlmJsonSchema.builder()
                .name("nested_array")
                .prop(LlmJsonSchema.arrayProp("matrix", LlmJsonSchema.arrayItem(LlmJsonSchema.numberItem())))
                .build();

        JsonNode output = requestStructuredOutput(schema, "Return matrix = [[1.1, 2.2], [3.3, 4.4]].");

        JsonNode matrix = output.path("matrix");
        assertThat(matrix.size()).isEqualTo(2);
        assertThat(matrix.get(0)).extracting(JsonNode::asDouble).containsExactly(1.1, 2.2);
        assertThat(matrix.get(1)).extracting(JsonNode::asDouble).containsExactly(3.3, 4.4);
    }

    @Test
    void structuredOutputWithRawSchemaArrayItems() throws IOException {
        LlmJsonSchema schema = LlmJsonSchema.builder()
                .name("raw_schema_items")
                .prop(LlmJsonSchema.arrayProp("events", LlmJsonSchema.rawSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "at", Map.of("type", "string"),
                                "kind", Map.of("type", "string")
                        ),
                        "required", List.of("at", "kind"),
                        "additionalProperties", false
                ))))
                .build();

        JsonNode output = requestStructuredOutput(schema, "Return events = [{at: 2026-05-26, kind: paid}].");

        JsonNode events = output.path("events");
        assertThat(events.size()).isEqualTo(1);
        assertThat(events.get(0).path("at").asText()).isEqualTo("2026-05-26");
        assertThat(events.get(0).path("kind").asText()).isEqualTo("paid");
    }

    private JsonNode requestStructuredOutput(LlmJsonSchema schema, String userPrompt) throws IOException {
        assumeTrue(isConfigured(), "Fill BASE_URL, API_KEY, and MODEL before running this live test.");
        OpenAiCompatibleChatModel chatModel = createModel();
        LlmChatRequest request = new LlmChatRequest();
        request.setMessages(List.of(
                new OpenAiChatMessage("system", """
                        Return only valid JSON. Follow the provided JSON schema exactly.
                        Do not rename fields. Do not add fields outside the schema.
                        """),
                new OpenAiChatMessage("user", userPrompt)
        ));
        request.setTemperature(0.0);
        request.setResponseFormat(schema);
        System.out.println("Structured output schema: " + schema.toJsonString());

        OpenAiChatCompletionResponse response = chatModel.chat(request);

        assertThat(response.getChoices()).isNotEmpty();
        Object content = response.getChoices().getFirst().getMessage().getContent();
        assertThat(content).isNotNull();
        System.out.println("Structured LLM response: " + content);
        return objectMapper.readTree(content.toString());
    }

    private static void assertBoolean(JsonNode node, String fieldName, boolean expected) {
        assertThat(node.path(fieldName).isBoolean())
                .as("Structured output must contain boolean field '%s'. Actual response node: %s", fieldName, node)
                .isTrue();
        assertThat(node.path(fieldName).asBoolean()).isEqualTo(expected);
    }

    private OpenAiCompatibleChatModel createModel() {
        return new OpenAiCompatibleChatModel(
                "live",
                normalizeBaseUrl(BASE_URL),
                cleanConfig(API_KEY),
                cleanConfig(MODEL),
                TIMEOUT
        );
    }

    private static boolean isConfigured() {
        return hasHttpScheme(normalizeBaseUrl(BASE_URL))
                && !cleanConfig(API_KEY).isBlank()
                && !"YOUR_API_KEY".equals(cleanConfig(API_KEY))
                && !cleanConfig(MODEL).isBlank()
                && !"YOUR_MODEL".equals(cleanConfig(MODEL));
    }

    private static String normalizeBaseUrl(String value) {
        String baseUrl = cleanConfig(value);
        String chatCompletionsPath = "/v1/chat/completions";
        if (baseUrl.endsWith(chatCompletionsPath)) {
            return baseUrl.substring(0, baseUrl.length() - chatCompletionsPath.length());
        }
        return baseUrl;
    }

    private static boolean hasHttpScheme(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }

    private static String cleanConfig(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.trim();
        while (cleaned.startsWith("'") || cleaned.startsWith("\"")) {
            cleaned = cleaned.substring(1).trim();
        }
        while (cleaned.endsWith("'") || cleaned.endsWith("\"")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }
        return cleaned;
    }

}
