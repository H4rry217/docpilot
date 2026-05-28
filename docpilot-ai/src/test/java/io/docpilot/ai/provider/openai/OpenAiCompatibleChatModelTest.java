package io.docpilot.ai.provider.openai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.docpilot.ai.AiModelException;
import io.docpilot.ai.AiModelMetadata;
import io.docpilot.ai.model.ChatMessage;
import io.docpilot.ai.model.ChatRequest;
import io.docpilot.ai.model.ChatResponse;
import io.docpilot.ai.model.ChatStreamEvent;
import io.docpilot.ai.model.ChatStreamEventType;
import io.docpilot.ai.model.JsonSchema;
import io.docpilot.ai.model.JsonSchemaResponseFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiCompatibleChatModelTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsOpenAiCompatibleChatRequestAndParsesResponse() throws IOException {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        startServer(exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            requestBody.set(readRequestBody(exchange));
            sendJson(exchange, 200, """
                    {
                      "id": "chatcmpl-1",
                      "object": "chat.completion",
                      "created": 1,
                      "model": "configured-model",
                      "system_fingerprint": "fp-test",
                      "choices": [
                        {
                          "index": 0,
                          "message": {
                            "role": "assistant",
                            "content": "Hello",
                            "reasoning_content": "I should answer briefly."
                          },
                          "finish_reason": "stop"
                        }
                      ],
                      "usage": {"prompt_tokens": 2, "completion_tokens": 1, "total_tokens": 3}
                    }
                    """);
        });
        OpenAiCompatibleChatModel model = createModel();
        ChatRequest request = new ChatRequest();
        ChatMessage assistantMessage = new ChatMessage("assistant", "prior answer");
        assistantMessage.setReasoningContent("prior reasoning");
        request.setMessages(List.of(new ChatMessage("user", "hello"), assistantMessage));
        request.setTemperature(0.7);
        request.setMaxOutputTokens(128);
        request.setResponseFormat(JsonSchemaResponseFormat.of("message_fields", JsonSchema.builder()
                .prop(JsonSchema.stringProp("answer"))
                .build()));
        request.setOption("custom_flag", true);

        ChatResponse response = model.chat(request);

        assertThat(model.metadata().provider()).isEqualTo("openai-compatible");
        assertThat(model.metadata().modelName()).isEqualTo("configured-model");
        assertThat(authorization.get()).isEqualTo("Bearer test-key");
        assertThat(contentType.get()).isEqualTo("application/json");
        assertThat(requestBody.get()).contains("\"model\":\"configured-model\"");
        assertThat(requestBody.get()).contains("\"stream\":false");
        assertThat(requestBody.get()).contains("\"max_tokens\":128");
        assertThat(requestBody.get()).contains("\"response_format\":{\"type\":\"json_schema\"");
        assertThat(requestBody.get()).contains("\"reasoning_content\":\"prior reasoning\"");
        assertThat(requestBody.get()).contains("\"custom_flag\":true");
        assertThat(response.getChoices()).hasSize(1);
        assertThat(response.getSystemFingerprint()).isEqualTo("fp-test");
        assertThat(response.getChoices().getFirst().getMessage().getContent()).isEqualTo("Hello");
        assertThat(response.getChoices().getFirst().getMessage().getReasoningContent()).isEqualTo("I should answer briefly.");
        assertThat(response.getUsage().getTotalTokens()).isEqualTo(3);
    }

    @Test
    void exposesConfiguredMetadata() throws IOException {
        startServer(exchange -> sendJson(exchange, 200, """
                {"id":"chatcmpl-1","object":"chat.completion","created":1,"model":"configured-model","choices":[]}
                """));
        AiModelMetadata metadata = AiModelMetadata.builder()
                .id("default")
                .provider("openai-compatible")
                .modelName("configured-model")
                .displayName("Configured Model")
                .contextWindowTokens(128000)
                .maxOutputTokens(8192)
                .build();

        OpenAiCompatibleChatModel model = new OpenAiCompatibleChatModel(
                metadata,
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "test-key",
                "configured-model",
                Duration.ofSeconds(5)
        );

        assertThat(model.metadata().displayName()).isEqualTo("Configured Model");
        assertThat(model.metadata().contextWindowTokens()).isEqualTo(128000);
        assertThat(model.metadata().maxOutputTokens()).isEqualTo(8192);
    }

    @Test
    void requestModelOverridesConfiguredModel() throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<>();
        startServer(exchange -> {
            requestBody.set(readRequestBody(exchange));
            sendJson(exchange, 200, """
                    {"id":"chatcmpl-1","object":"chat.completion","created":1,"model":"override-model","choices":[]}
                    """);
        });
        OpenAiCompatibleChatModel model = createModel();
        ChatRequest request = new ChatRequest();
        request.setModel("override-model");
        request.setMessages(List.of(new ChatMessage("user", "hello")));

        model.chat(request);

        assertThat(requestBody.get()).contains("\"model\":\"override-model\"");
    }

    @Test
    void sendsToolsAndParsesChatToolCalls() throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<>();
        startServer(exchange -> {
            requestBody.set(readRequestBody(exchange));
            sendJson(exchange, 200, """
                    {
                      "id": "chatcmpl-tool-1",
                      "object": "chat.completion",
                      "created": 1,
                      "model": "configured-model",
                      "choices": [
                        {
                          "index": 0,
                          "message": {
                            "role": "assistant",
                            "content": null,
                            "tool_calls": [
                              {
                                "id": "call_weather",
                                "type": "function",
                                "function": {
                                  "name": "get_weather",
                                  "arguments": "{\\"city\\":\\"杭州\\"}"
                                }
                              }
                            ]
                          },
                          "finish_reason": "tool_calls"
                        }
                      ]
                    }
                    """);
        });
        OpenAiCompatibleChatModel model = createModel();
        ChatRequest request = new ChatRequest();
        request.setMessages(List.of(new ChatMessage("user", "杭州天气怎么样？")));
        request.setOption("tools", List.of(Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "get_weather",
                        "description", "Get weather by city.",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of("city", Map.of("type", "string")),
                                "required", List.of("city")
                        )
                )
        )));
        request.setOption("tool_choice", "auto");

        ChatResponse response = model.chat(request);

        assertThat(requestBody.get()).contains("\"tools\":[");
        assertThat(requestBody.get()).contains("\"tool_choice\":\"auto\"");
        assertThat(response.getChoices()).hasSize(1);
        assertThat(response.getChoices().getFirst().getFinishReason()).isEqualTo("tool_calls");
        assertThat(response.getChoices().getFirst().getMessage().getToolCalls().toString())
                .contains("call_weather")
                .contains("get_weather")
                .contains("杭州");
    }

    @Test
    void parsesServerSentEventStream() throws IOException {
        startServer(exchange -> {
            readRequestBody(exchange);
            byte[] response = """
                    data: {"id":"chunk-1","object":"chat.completion.chunk","created":1,"model":"configured-model","system_fingerprint":"fp-stream","choices":[{"index":0,"delta":{"reasoning_content":"Thinking first."},"finish_reason":null}]}

                    data: {"id":"chunk-1","object":"chat.completion.chunk","created":1,"model":"configured-model","choices":[{"index":0,"delta":{"role":"assistant","content":"Hel"},"finish_reason":null}]}

                    data: {"id":"chunk-1","object":"chat.completion.chunk","created":1,"model":"configured-model","choices":[{"index":0,"delta":{"content":"lo"},"finish_reason":"stop"}]}

                    data: [DONE]

                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        OpenAiCompatibleChatModel model = createModel();
        ChatRequest request = new ChatRequest();
        request.setMessages(List.of(new ChatMessage("user", "hello")));

        List<ChatStreamEvent> events = model.stream(request)
                .collectList()
                .block(Duration.ofSeconds(5));

        assertThat(events).hasSize(3);
        assertThat(events.get(0).getType()).isEqualTo(ChatStreamEventType.REASONING_DELTA);
        assertThat(events.get(0).getSystemFingerprint()).isEqualTo("fp-stream");
        assertThat(events.get(0).getDelta().getReasoningContent()).isEqualTo("Thinking first.");
        assertThat(events.get(0).getDelta().getAdditionalProperties()).doesNotContainKey("reasoning_content");
        assertThat(events.get(1).getType()).isEqualTo(ChatStreamEventType.MESSAGE_DELTA);
        assertThat(events.get(1).getDelta().getContent()).isEqualTo("Hel");
        assertThat(events.get(2).getDelta().getContent()).isEqualTo("lo");
        assertThat(events.get(2).getFinishReason()).isEqualTo("stop");
    }

    @Test
    void parsesServerSentToolCallDeltas() throws IOException {
        startServer(exchange -> {
            readRequestBody(exchange);
            byte[] response = """
                    data: {"id":"chunk-tool-1","object":"chat.completion.chunk","created":1,"model":"configured-model","choices":[{"index":0,"delta":{"role":"assistant","tool_calls":[{"index":0,"id":"call_weather","type":"function","function":{"name":"get_weather","arguments":""}}]},"finish_reason":null}]}

                    data: {"id":"chunk-tool-1","object":"chat.completion.chunk","created":1,"model":"configured-model","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\\\"city\\\":\\\"杭州\\\"}"}}]},"finish_reason":"tool_calls"}]}

                    data: [DONE]

                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        OpenAiCompatibleChatModel model = createModel();
        ChatRequest request = new ChatRequest();
        request.setMessages(List.of(new ChatMessage("user", "杭州天气怎么样？")));

        List<ChatStreamEvent> events = model.stream(request)
                .collectList()
                .block(Duration.ofSeconds(5));

        assertThat(events).hasSize(2);
        assertThat(events.get(0).getType()).isEqualTo(ChatStreamEventType.TOOL_CALL_DELTA);
        assertThat(events.get(0).getDelta().getToolCalls().toString())
                .contains("call_weather")
                .contains("get_weather");
        assertThat(events.get(1).getType()).isEqualTo(ChatStreamEventType.TOOL_CALL_DELTA);
        assertThat(events.get(1).getDelta().getToolCalls().toString()).contains("杭州");
        assertThat(events.get(1).getFinishReason()).isEqualTo("tool_calls");
    }

    @Test
    void validatesRequiredConfiguration() {
        assertThatThrownBy(() -> new OpenAiCompatibleChatModel("id", null, "key", "model", Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("baseUrl is required");
        assertThatThrownBy(() -> new OpenAiCompatibleChatModel("id", "http://example.com", " ", "model", Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiKey is required");
        assertThatThrownBy(() -> new OpenAiCompatibleChatModel("id", "http://example.com", "key", "", Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("model is required");
    }

    @Test
    void reportsHttpErrors() throws IOException {
        startServer(exchange -> sendJson(exchange, 401, "{\"error\":{\"message\":\"bad key\"}}"));
        OpenAiCompatibleChatModel model = createModel();

        assertThatThrownBy(() -> model.chat(new ChatRequest()))
                .isInstanceOf(AiModelException.class)
                .hasMessageContaining("HTTP 401")
                .hasMessageContaining("bad key");
    }

    private OpenAiCompatibleChatModel createModel() {
        return new OpenAiCompatibleChatModel(
                "default",
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "test-key",
                "configured-model",
                Duration.ofSeconds(5)
        );
    }

    private void startServer(HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", handler);
        server.start();
    }

    private static String readRequestBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] response = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

}
