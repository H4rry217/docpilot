package io.docpilot.ai.openai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.docpilot.ai.AiModelException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
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
                      "choices": [
                        {
                          "index": 0,
                          "message": {"role": "assistant", "content": "Hello"},
                          "finish_reason": "stop"
                        }
                      ],
                      "usage": {"prompt_tokens": 2, "completion_tokens": 1, "total_tokens": 3}
                    }
                    """);
        });
        OpenAiCompatibleChatModel model = createModel();
        LlmChatRequest request = new LlmChatRequest();
        request.setMessages(List.of(new OpenAiChatMessage("user", "hello")));
        request.setTemperature(0.7);
        request.setAdditionalProperty("custom_flag", true);

        OpenAiChatCompletionResponse response = model.chat(request);

        assertThat(authorization.get()).isEqualTo("Bearer test-key");
        assertThat(contentType.get()).isEqualTo("application/json");
        assertThat(requestBody.get()).contains("\"model\":\"configured-model\"");
        assertThat(requestBody.get()).contains("\"stream\":false");
        assertThat(requestBody.get()).contains("\"custom_flag\":true");
        assertThat(response.getChoices()).hasSize(1);
        assertThat(response.getChoices().getFirst().getMessage().getContent()).isEqualTo("Hello");
        assertThat(response.getUsage().getTotalTokens()).isEqualTo(3);
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
        LlmChatRequest request = new LlmChatRequest();
        request.setModel("override-model");
        request.setMessages(List.of(new OpenAiChatMessage("user", "hello")));

        model.chat(request);

        assertThat(requestBody.get()).contains("\"model\":\"override-model\"");
    }

    @Test
    void parsesServerSentEventStream() throws IOException {
        startServer(exchange -> {
            readRequestBody(exchange);
            byte[] response = """
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
        LlmChatRequest request = new LlmChatRequest();
        request.setMessages(List.of(new OpenAiChatMessage("user", "hello")));

        List<OpenAiChatCompletionResponse> chunks = model.stream(request)
                .collectList()
                .block(Duration.ofSeconds(5));

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getChoices().getFirst().getDelta().getContent()).isEqualTo("Hel");
        assertThat(chunks.get(1).getChoices().getFirst().getDelta().getContent()).isEqualTo("lo");
        assertThat(chunks.get(1).getChoices().getFirst().getFinishReason()).isEqualTo("stop");
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

        assertThatThrownBy(() -> model.chat(new LlmChatRequest()))
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
