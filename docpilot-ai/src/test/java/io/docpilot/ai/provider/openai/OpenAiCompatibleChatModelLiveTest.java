package io.docpilot.ai.provider.openai;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.docpilot.ai.model.ChatMessage;
import io.docpilot.ai.model.ChatRequest;
import io.docpilot.ai.model.ChatResponse;
import io.docpilot.ai.model.ChatStreamEvent;
import io.docpilot.ai.model.ChatStreamEventType;
import io.docpilot.ai.model.JsonSchema;
import io.docpilot.ai.model.JsonSchemaResponseFormat;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Live smoke tests for manually verifying an OpenAI-compatible provider.
 *
 * Run with system properties, for example:
 * mvn -pl docpilot-ai -Dtest=OpenAiCompatibleChatModelLiveTest
 * -Ddocpilot.ai.live.base-url=https://example.com
 * -Ddocpilot.ai.live.api-key=sk-...
 * -Ddocpilot.ai.live.model=qwen3.6-flash test
 */
class OpenAiCompatibleChatModelLiveTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String BASE_URL_PROPERTY = "docpilot.ai.live.base-url";
    private static final String API_KEY_PROPERTY = "docpilot.ai.live.api-key";
    private static final String MODEL_PROPERTY = "docpilot.ai.live.model";
    private static final String TOOL_ENABLE_THINKING_PROPERTY = "docpilot.ai.live.tool-enable-thinking";
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    @Test
    void chatsWithRealLlm() {
        assumeTrue(isConfigured(), "Fill BASE_URL, API_KEY, and MODEL before running this live test.");
        OpenAiCompatibleChatModel chatModel = createModel();
        ChatRequest request = new ChatRequest();
        request.setMessages(List.of(
                new ChatMessage("system", "You are a concise assistant."),
                new ChatMessage("user", "Answer in one short Chinese sentence: what is DocPilot?")
        ));
        request.setTemperature(0.2);

        ChatResponse response = chatModel.chat(request);

        assertThat(response.getChoices()).isNotEmpty();
        Object content = response.getChoices().getFirst().getMessage().getContent();
        assertThat(content).isNotNull();
        System.out.println("LLM response: " + content);
    }

    @Test
    void chatsWithRealLlmToolCall() throws JacksonException {
        assumeTrue(isConfigured(), "Fill BASE_URL, API_KEY, and MODEL before running this live test.");
        OpenAiCompatibleChatModel chatModel = createModel();

        ChatResponse response = chatModel.chat(weatherToolRequest());

        System.out.println("Tool call chat response: " + objectMapper.writeValueAsString(response));
        assertThat(response.getChoices()).isNotEmpty();
        assertThat(response.getChoices().getFirst().getFinishReason()).isEqualTo("tool_calls");
        Object toolCalls = response.getChoices().getFirst().getMessage().getToolCalls();
        assertThat(toolCalls).isNotNull();
        assertThat(toolCalls.toString())
                .contains("get_weather")
                .contains("Hangzhou");
    }

    @Test
    void streamsWithRealLlmConversation() {
        assumeTrue(isConfigured(), "Fill BASE_URL, API_KEY, and MODEL before running this live test.");
        OpenAiCompatibleChatModel chatModel = createModel();
        ChatRequest request = new ChatRequest();
        request.setMessages(List.of(
                new ChatMessage("system", "You are a concise assistant. Answer in Chinese."),
                new ChatMessage("user", "我的名字是小航，请记住。"),
                new ChatMessage("assistant", "好的，我记住了，你叫小航。"),
                new ChatMessage("user", "用两句很短的话介绍 DocPilot，并叫出我的名字。")
        ));
        request.setTemperature(0.2);

        StringBuilder streamedContent = new StringBuilder();
        List<ChatStreamEvent> events = chatModel.stream(request)
                .doOnNext(event -> appendAndPrintStreamEvent(event, streamedContent))
                .collectList()
                .block(TIMEOUT.plusSeconds(10));

        System.out.println();
        System.out.println("Streamed LLM response: " + streamedContent);
        assertThat(events).isNotNull().isNotEmpty();
        assertThat(events).anySatisfy(event -> assertThat(event.getType()).isEqualTo(ChatStreamEventType.MESSAGE_DELTA));
        assertThat(streamedContent.toString()).isNotBlank();
    }

    @Test
    void streamsWithRealLlmToolCall() {
        assumeTrue(isConfigured(), "Fill BASE_URL, API_KEY, and MODEL before running this live test.");
        OpenAiCompatibleChatModel chatModel = createModel();

        List<ChatStreamEvent> events = chatModel.stream(weatherToolRequest())
                .doOnNext(event -> System.out.println("Tool call stream event: " + describeStreamEvent(event)))
                .collectList()
                .block(TIMEOUT.plusSeconds(10));

        StringBuilder toolCallPayload = new StringBuilder();
        if (events != null) {
            for (ChatStreamEvent event : events) {
                if (event.getDelta() != null && event.getDelta().getToolCalls() != null) {
                    toolCallPayload.append(event.getDelta().getToolCalls());
                }
            }
        }

        System.out.println("Streamed tool call payload: " + toolCallPayload);
        assertThat(events).isNotNull().isNotEmpty();
        assertThat(events).anySatisfy(event -> assertThat(event.getType()).isEqualTo(ChatStreamEventType.TOOL_CALL_DELTA));
        assertThat(toolCallPayload.toString())
                .contains("get_weather")
                .contains("Hang")
                .contains("zhou");
    }

    @Test
    void printsRawStreamEventsWithRealLlmConversation() throws IOException, InterruptedException {
        assumeTrue(isConfigured(), "Fill BASE_URL, API_KEY, and MODEL before running this live test.");
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest httpRequest = HttpRequest.newBuilder(streamEndpoint())
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + cleanConfig(liveConfig(API_KEY_PROPERTY)))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(rawStreamRequestBody(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<java.io.InputStream> response = httpClient.send(
                httpRequest,
                HttpResponse.BodyHandlers.ofInputStream()
        );
        assertThat(response.statusCode()).isBetween(200, 299);

        try (var reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                System.out.println("Raw stream line: " + line);
                if ("data: [DONE]".equals(line.trim())) {
                    break;
                }
            }
        }
    }

    @Test
    void structuredOutputWithPrimitiveTypes() throws IOException {
        JsonSchemaResponseFormat schema = JsonSchemaResponseFormat.of("primitive_types",
                JsonSchema.builder().props(
                        JsonSchema.stringProp("title"),
                        JsonSchema.numberProp("amount"),
                        JsonSchema.integerProp("count"),
                        JsonSchema.booleanProp("paid"),
                        JsonSchema.nullProp("deleted_at")
                )
                .build());

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
        JsonSchemaResponseFormat schema = JsonSchemaResponseFormat.of("enum_type",
                JsonSchema.builder()
                        .prop(JsonSchema.enumProp("status", List.of("draft", "paid", "cancelled")))
                        .build());

        JsonNode output = requestStructuredOutput(schema, "Return status = paid.");

        assertThat(output.path("status").asText()).isEqualTo("paid");
    }

    @Test
    void structuredOutputWithObject() throws IOException {
        JsonSchemaResponseFormat schema = JsonSchemaResponseFormat.of("object_type",
                JsonSchema.builder()
                        .prop(JsonSchema.objectProp("customer")
                                .prop(JsonSchema.stringProp("name"))
                                .prop(JsonSchema.integerProp("age"))
                                .prop(JsonSchema.booleanProp("vip")))
                        .build());

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
        JsonSchemaResponseFormat schema = JsonSchemaResponseFormat.of("primitive_arrays",
                JsonSchema.builder().props(
                        JsonSchema.arrayProp("tags", JsonSchema.stringItem()),
                        JsonSchema.arrayProp("scores", JsonSchema.numberItem()),
                        JsonSchema.arrayProp("flags", JsonSchema.booleanItem())
                )
                .build());

        JsonNode output = requestStructuredOutput(schema, """
                Return tags = urgent, finance; scores = 98.5, 87.0; flags = true, false.
                """);

        assertThat(output.path("tags")).extracting(JsonNode::asText).containsExactly("urgent", "finance");
        assertThat(output.path("scores")).extracting(JsonNode::asDouble).containsExactly(98.5, 87.0);
        assertThat(output.path("flags")).extracting(JsonNode::asBoolean).containsExactly(true, false);
    }

    @Test
    void structuredOutputWithObjectArray() throws IOException {
        JsonSchemaResponseFormat schema = JsonSchemaResponseFormat.of("object_array",
                JsonSchema.builder()
                        .prop(JsonSchema.arrayProp("items", JsonSchema.objectItem()
                                .prop(JsonSchema.stringProp("sku"))
                                .prop(JsonSchema.integerProp("quantity"))
                                .prop(JsonSchema.numberProp("price"))))
                        .build());

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
        JsonSchemaResponseFormat schema = JsonSchemaResponseFormat.of("nested_array",
                JsonSchema.builder()
                        .prop(JsonSchema.arrayProp("matrix", JsonSchema.arrayItem(JsonSchema.numberItem())))
                        .build());

        JsonNode output = requestStructuredOutput(schema, "Return matrix = [[1.1, 2.2], [3.3, 4.4]].");

        JsonNode matrix = output.path("matrix");
        assertThat(matrix.size()).isEqualTo(2);
        assertThat(matrix.get(0)).extracting(JsonNode::asDouble).containsExactly(1.1, 2.2);
        assertThat(matrix.get(1)).extracting(JsonNode::asDouble).containsExactly(3.3, 4.4);
    }

    @Test
    void structuredOutputWithRawSchemaArrayItems() throws IOException {
        JsonSchemaResponseFormat schema = JsonSchemaResponseFormat.of("raw_schema_items",
                JsonSchema.builder()
                        .prop(JsonSchema.arrayProp("events", JsonSchema.rawSchema(Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "at", Map.of("type", "string"),
                                        "kind", Map.of("type", "string")
                                ),
                                "required", List.of("at", "kind"),
                                "additionalProperties", false
                        ))))
                        .build());

        JsonNode output = requestStructuredOutput(schema, "Return events = [{at: 2026-05-26, kind: paid}].");

        JsonNode events = output.path("events");
        assertThat(events.size()).isEqualTo(1);
        assertThat(events.get(0).path("at").asText()).isEqualTo("2026-05-26");
        assertThat(events.get(0).path("kind").asText()).isEqualTo("paid");
    }

    private JsonNode requestStructuredOutput(JsonSchemaResponseFormat schema, String userPrompt) throws IOException {
        assumeTrue(isConfigured(), "Fill BASE_URL, API_KEY, and MODEL before running this live test.");
        OpenAiCompatibleChatModel chatModel = createModel();
        ChatRequest request = new ChatRequest();
        request.setMessages(List.of(
                new ChatMessage("system", """
                        Return only valid JSON. Follow the provided JSON schema exactly.
                        Do not rename fields. Do not add fields outside the schema.
                        """),
                new ChatMessage("user", userPrompt)
        ));
        request.setTemperature(0.0);
        request.setResponseFormat(schema);
        System.out.println("Structured output schema: " + schema.toJsonString());

        ChatResponse response = chatModel.chat(request);

        assertThat(response.getChoices()).isNotEmpty();
        Object content = response.getChoices().getFirst().getMessage().getContent();
        assertThat(content).isNotNull();
        System.out.println("Structured LLM response: " + content);
        return objectMapper.readTree(content.toString());
    }

    private static ChatRequest weatherToolRequest() {
        ChatRequest request = new ChatRequest();
        request.setMessages(List.of(
                new ChatMessage("system", "You are a tool-calling assistant. Use the provided tool when asked for weather."),
                new ChatMessage("user", "Use the get_weather tool for Hangzhou. Do not answer directly.")
        ));
        request.setTemperature(0.0);
        request.setOption("tools", List.of(weatherTool()));
        request.setOption("tool_choice", Map.of(
                "type", "function",
                "function", Map.of("name", "get_weather")
        ));
        request.setOption("enable_thinking", Boolean.parseBoolean(liveConfig(TOOL_ENABLE_THINKING_PROPERTY, "false")));
        return request;
    }

    private static Map<String, Object> weatherTool() {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "get_weather",
                        "description", "Get current weather by city.",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "city", Map.of(
                                                "type", "string",
                                                "description", "City name, for example Hangzhou."
                                        )
                                ),
                                "required", List.of("city"),
                                "additionalProperties", false
                        )
                )
        );
    }

    private String rawStreamRequestBody() throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", cleanConfig(liveConfig(MODEL_PROPERTY)));
        payload.put("messages", List.of(
                Map.of("role", "system", "content", "You are a concise assistant. Answer in Chinese."),
                Map.of("role", "user", "content", "我的名字是小航，请记住。"),
                Map.of("role", "assistant", "content", "好的，我记住了，你叫小航。"),
                Map.of("role", "user", "content", "用两句很短的话介绍 DocPilot，并叫出我的名字。")
        ));
        payload.put("temperature", 0.2);
        payload.put("stream", true);
        return objectMapper.writeValueAsString(payload);
    }

    private static URI streamEndpoint() {
        return URI.create(normalizeBaseUrl(liveConfig(BASE_URL_PROPERTY)) + "/v1/chat/completions");
    }

    private static void appendAndPrintStreamEvent(ChatStreamEvent event, StringBuilder streamedContent) {
        Object content = event.getDelta() == null ? null : event.getDelta().getContent();
        if (event.getType() == ChatStreamEventType.MESSAGE_DELTA && content != null) {
            streamedContent.append(content);
        }
        System.out.println("Stream event: " + describeStreamEvent(event));
    }

    private static String describeStreamEvent(ChatStreamEvent event) {
        try {
            return new ObjectMapper().writeValueAsString(event);
        } catch (JacksonException e) {
            throw new RuntimeException(e);
        }
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
                normalizeBaseUrl(liveConfig(BASE_URL_PROPERTY)),
                cleanConfig(liveConfig(API_KEY_PROPERTY)),
                cleanConfig(liveConfig(MODEL_PROPERTY)),
                TIMEOUT
        );
    }

    private static boolean isConfigured() {
        String baseUrl = cleanConfig(liveConfig(BASE_URL_PROPERTY));
        String apiKey = cleanConfig(liveConfig(API_KEY_PROPERTY));
        String model = cleanConfig(liveConfig(MODEL_PROPERTY));
        return hasHttpScheme(normalizeBaseUrl(baseUrl))
                && !apiKey.isBlank()
                && !model.isBlank();
    }

    private static String normalizeBaseUrl(String value) {
        String baseUrl = cleanConfig(value);
        String chatCompletionsPath = "/v1/chat/completions";
        if (baseUrl.endsWith(chatCompletionsPath)) {
            return baseUrl.substring(0, baseUrl.length() - chatCompletionsPath.length());
        }
        if (baseUrl.endsWith("/v1")) {
            return baseUrl.substring(0, baseUrl.length() - "/v1".length());
        }
        return baseUrl;
    }

    private static String liveConfig(String propertyName) {
        return System.getProperty(propertyName, "");
    }

    private static String liveConfig(String propertyName, String defaultValue) {
        return System.getProperty(propertyName, defaultValue);
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
