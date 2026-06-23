package io.docpilot.ai.provider.openai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.docpilot.ai.AiModelMetadata;
import io.docpilot.ai.model.EmbeddingRequest;
import io.docpilot.ai.model.EmbeddingResponse;
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

class OpenAiCompatibleEmbeddingModelTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsConfiguredDimensionsAndParsesEmbeddings() throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<>();
        startServer(exchange -> {
            requestBody.set(readRequestBody(exchange));
            sendJson(exchange, 200, """
                    {
                      "object": "list",
                      "model": "embedding-model",
                      "data": [
                        {"object": "embedding", "index": 0, "embedding": [0.1, -0.2, 0.3]}
                      ],
                      "usage": {"prompt_tokens": 2, "total_tokens": 2}
                    }
                    """);
        });
        OpenAiCompatibleEmbeddingModel model = new OpenAiCompatibleEmbeddingModel(
                AiModelMetadata.builder()
                        .id("embedding")
                        .provider("openai-compatible")
                        .modelName("embedding-model")
                        .build(),
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "test-key",
                "embedding-model",
                1024,
                Duration.ofSeconds(5)
        );
        EmbeddingRequest request = new EmbeddingRequest();
        request.setInputs(List.of("hello"));

        EmbeddingResponse response = model.embed(request);

        assertThat(requestBody.get()).contains("\"model\":\"embedding-model\"");
        assertThat(requestBody.get()).contains("\"input\":[\"hello\"]");
        assertThat(requestBody.get()).contains("\"dimensions\":1024");
        assertThat(response.getEmbeddings()).hasSize(1);
        assertThat(response.getEmbeddings().getFirst()).containsExactly(0.1F, -0.2F, 0.3F);
    }

    @Test
    void requestDimensionsOverrideConfiguredDimensions() throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<>();
        startServer(exchange -> {
            requestBody.set(readRequestBody(exchange));
            sendJson(exchange, 200, """
                    {"object":"list","model":"embedding-model","data":[]}
                    """);
        });
        OpenAiCompatibleEmbeddingModel model = new OpenAiCompatibleEmbeddingModel(
                AiModelMetadata.of("embedding"),
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "test-key",
                "embedding-model",
                1024,
                Duration.ofSeconds(5)
        );
        EmbeddingRequest request = new EmbeddingRequest();
        request.setInputs(List.of("hello"));
        request.setDimensions(2560);

        model.embed(request);

        assertThat(requestBody.get()).contains("\"dimensions\":2560");
    }

    @Test
    void sendsConfiguredCustomHeadersForEmbeddingRequest() throws IOException {
        AtomicReference<String> tenantHeader = new AtomicReference<>();
        AtomicReference<String> hostHeader = new AtomicReference<>();
        startServer(exchange -> {
            tenantHeader.set(exchange.getRequestHeaders().getFirst("X-Provider-Tenant"));
            hostHeader.set(exchange.getRequestHeaders().getFirst("Host"));
            readRequestBody(exchange);
            sendJson(exchange, 200, """
                    {"object":"list","model":"embedding-model","data":[]}
                    """);
        });
        OpenAiCompatibleEmbeddingModel model = new OpenAiCompatibleEmbeddingModel(
                AiModelMetadata.of("embedding"),
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "test-key",
                "embedding-model",
                1024,
                Duration.ofSeconds(5),
                Map.of(
                        "X-Provider-Tenant", "tenant-a",
                        "Host", "embedding-tenant.example.com"
                )
        );

        model.embed(new EmbeddingRequest());

        assertThat(tenantHeader.get()).isEqualTo("tenant-a");
        assertThat(hostHeader.get()).isEqualTo("embedding-tenant.example.com");
    }

    @Test
    void configuredHeadersOverrideDefaultEmbeddingHeaders() throws IOException {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> accept = new AtomicReference<>();
        startServer(exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            accept.set(exchange.getRequestHeaders().getFirst("Accept"));
            readRequestBody(exchange);
            sendJson(exchange, 200, """
                    {"object":"list","model":"embedding-model","data":[]}
                    """);
        });
        OpenAiCompatibleEmbeddingModel model = new OpenAiCompatibleEmbeddingModel(
                AiModelMetadata.of("embedding"),
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "test-key",
                "embedding-model",
                1024,
                Duration.ofSeconds(5),
                Map.of(
                        "Authorization", "Bearer gateway-key",
                        "Accept", "application/vnd.gateway+json"
                )
        );

        model.embed(new EmbeddingRequest());

        assertThat(authorization.get()).isEqualTo("Bearer gateway-key");
        assertThat(accept.get()).isEqualTo("application/vnd.gateway+json");
    }

    private void startServer(HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/embeddings", handler);
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
