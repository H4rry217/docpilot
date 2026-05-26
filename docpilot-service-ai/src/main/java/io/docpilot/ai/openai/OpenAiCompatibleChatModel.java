package io.docpilot.ai.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.docpilot.ai.AiChatModel;
import io.docpilot.ai.AiModelException;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class OpenAiCompatibleChatModel implements AiChatModel {

    private static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    private final String id;
    private final URI endpoint;
    private final String apiKey;
    private final String configuredModel;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleChatModel(String id,
                                     String baseUrl,
                                     String apiKey,
                                     String configuredModel,
                                     Duration timeout) {
        this(id, baseUrl, apiKey, configuredModel, timeout, HttpClient.newHttpClient(), new ObjectMapper());
    }

    public OpenAiCompatibleChatModel(String id,
                                     String baseUrl,
                                     String apiKey,
                                     String configuredModel,
                                     Duration timeout,
                                     HttpClient httpClient,
                                     ObjectMapper objectMapper) {
        this.id = requireText(id, "AI model id must not be blank");
        this.endpoint = URI.create(trimTrailingSlash(requireText(baseUrl, "OpenAI-compatible baseUrl is required"))
                + CHAT_COMPLETIONS_PATH);
        this.apiKey = requireText(apiKey, "OpenAI-compatible apiKey is required for model: " + id);
        this.configuredModel = requireText(configuredModel, "OpenAI-compatible model is required for model: " + id);
        this.timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
        this.httpClient = httpClient == null ? HttpClient.newHttpClient() : httpClient;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public OpenAiChatCompletionResponse chat(LlmChatRequest request) {
        LlmChatRequest preparedRequest = prepareRequest(request, false);
        HttpRequest httpRequest = buildRequest(preparedRequest, "application/json");
        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            ensureSuccess(response.statusCode(), response.body());
            return objectMapper.readValue(response.body(), OpenAiChatCompletionResponse.class);
        } catch (IOException exception) {
            throw new AiModelException("Failed to call AI model: " + id, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiModelException("AI model call was interrupted: " + id, exception);
        }
    }

    @Override
    public Flux<OpenAiChatCompletionResponse> stream(LlmChatRequest request) {
        return Flux.defer(() -> {
            LlmChatRequest preparedRequest = prepareRequest(request, true);
            HttpRequest httpRequest = buildRequest(preparedRequest, "text/event-stream");
            try {
                HttpResponse<InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    String body = readAll(response.body());
                    return Flux.error(new AiModelException("AI model stream failed with HTTP "
                            + response.statusCode() + ": " + body));
                }
                return decodeServerSentEvents(response.body());
            } catch (IOException exception) {
                return Flux.error(new AiModelException("Failed to stream AI model: " + id, exception));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return Flux.error(new AiModelException("AI model stream was interrupted: " + id, exception));
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private LlmChatRequest prepareRequest(LlmChatRequest request, boolean stream) {
        LlmChatRequest preparedRequest = request == null ? new LlmChatRequest() : request.copy();
        if (preparedRequest.getModel() == null || preparedRequest.getModel().isBlank()) {
            preparedRequest.setModel(configuredModel);
        }
        preparedRequest.setStream(stream);
        return preparedRequest;
    }

    private HttpRequest buildRequest(LlmChatRequest request, String accept) {
        try {
            String body = objectMapper.writeValueAsString(request.toPayload());
            return HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", accept)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
        } catch (IOException exception) {
            throw new AiModelException("Failed to serialize AI request for model: " + id, exception);
        }
    }

    private Flux<OpenAiChatCompletionResponse> decodeServerSentEvents(InputStream inputStream) {
        return Flux.using(
                () -> new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)),
                reader -> Flux.generate(() -> false, (completed, sink) -> {
                    if (completed) {
                        sink.complete();
                        return true;
                    }
                    try {
                        while (true) {
                            String line = reader.readLine();
                            if (line == null) {
                                sink.complete();
                                return true;
                            }
                            String trimmed = line.trim();
                            if (trimmed.isEmpty() || trimmed.startsWith(":") || !trimmed.startsWith("data:")) {
                                continue;
                            }
                            String data = trimmed.substring("data:".length()).trim();
                            if ("[DONE]".equals(data)) {
                                sink.complete();
                                return true;
                            }
                            sink.next(objectMapper.readValue(data, OpenAiChatCompletionResponse.class));
                            return false;
                        }
                    } catch (IOException exception) {
                        sink.error(new AiModelException("Failed to parse AI stream for model: " + id, exception));
                        return true;
                    }
                }),
                reader -> {
                    try {
                        reader.close();
                    } catch (IOException ignored) {
                    }
                }
        );
    }

    private void ensureSuccess(int statusCode, String body) {
        if (statusCode < 200 || statusCode >= 300) {
            throw new AiModelException("AI model call failed with HTTP " + statusCode + ": " + body);
        }
    }

    private String readAll(InputStream inputStream) throws IOException {
        try (inputStream) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String trimTrailingSlash(String value) {
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

}
