package io.docpilot.ai.provider.openai;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.docpilot.ai.AiEmbeddingModel;
import io.docpilot.ai.AiModelException;
import io.docpilot.ai.AiModelMetadata;
import io.docpilot.ai.model.EmbeddingRequest;
import io.docpilot.ai.model.EmbeddingResponse;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * {@link AiEmbeddingModel} implementation for providers exposing OpenAI-compatible embeddings.
 */
public class OpenAiCompatibleEmbeddingModel implements AiEmbeddingModel {

    private static final String EMBEDDINGS_PATH = "/v1/embeddings";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    private final String id;
    private final AiModelMetadata metadata;
    private final URI endpoint;
    private final String apiKey;
    private final String configuredModel;
    private final Integer configuredDimensions;
    private final Map<String, String> customHeaders;
    private final Duration timeout;
    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleEmbeddingModel(AiModelMetadata metadata,
                                          String baseUrl,
                                          String apiKey,
                                          String configuredModel,
                                          Duration timeout) {
        this(metadata, baseUrl, apiKey, configuredModel, null, timeout);
    }

    public OpenAiCompatibleEmbeddingModel(AiModelMetadata metadata,
                                          String baseUrl,
                                          String apiKey,
                                          String configuredModel,
                                          Duration timeout,
                                          Map<String, String> customHeaders) {
        this(metadata, baseUrl, apiKey, configuredModel, null, timeout, customHeaders);
    }

    public OpenAiCompatibleEmbeddingModel(AiModelMetadata metadata,
                                          String baseUrl,
                                          String apiKey,
                                          String configuredModel,
                                          Integer configuredDimensions,
                                          Duration timeout) {
        this(metadata, baseUrl, apiKey, configuredModel, configuredDimensions, timeout, Map.of());
    }

    public OpenAiCompatibleEmbeddingModel(AiModelMetadata metadata,
                                          String baseUrl,
                                          String apiKey,
                                          String configuredModel,
                                          Integer configuredDimensions,
                                          Duration timeout,
                                          Map<String, String> customHeaders) {
        this(metadata, baseUrl, apiKey, configuredModel, configuredDimensions, timeout,
                customHeaders, HttpClients.createDefault(), new ObjectMapper());
    }

    private OpenAiCompatibleEmbeddingModel(AiModelMetadata metadata,
                                           String baseUrl,
                                           String apiKey,
                                           String configuredModel,
                                           Integer configuredDimensions,
                                           Duration timeout,
                                           Map<String, String> customHeaders,
                                           CloseableHttpClient httpClient,
                                           ObjectMapper objectMapper) {
        this.metadata = Objects.requireNonNull(metadata, "AI embedding metadata must not be null");
        this.id = metadata.id();
        this.endpoint = URI.create(trimTrailingSlash(requireText(baseUrl, "OpenAI-compatible baseUrl is required"))
                + EMBEDDINGS_PATH);
        this.apiKey = requireText(apiKey, "OpenAI-compatible apiKey is required for embedding model: " + id);
        this.configuredModel = requireText(configuredModel, "OpenAI-compatible model is required for embedding model: " + id);
        this.configuredDimensions = validateDimensions(configuredDimensions);
        this.customHeaders = customHeaders == null ? Map.of() : Map.copyOf(customHeaders);
        this.timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
        this.httpClient = httpClient == null ? HttpClients.createDefault() : httpClient;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public AiModelMetadata metadata() {
        return metadata;
    }

    @Override
    public EmbeddingResponse embed(EmbeddingRequest request) {
        EmbeddingRequest preparedRequest = prepareRequest(request);
        try {
            String body = objectMapper.writeValueAsString(toOpenAiPayload(preparedRequest));
            HttpPost httpRequest = buildRequest(body);
            return httpClient.execute(httpRequest, response -> {
                String responseBody = readResponseBody(response);
                ensureSuccess(response.getCode(), responseBody);
                try {
                    return toEmbeddingResponse(objectMapper.readTree(responseBody));
                } catch (JacksonException exception) {
                    throw new AiModelException("Failed to parse embedding response for model: " + id, exception);
                }
            });
        } catch (IOException | JacksonException exception) {
            throw new AiModelException("Failed to call embedding model: " + id, exception);
        }
    }

    private HttpPost buildRequest(String body) {
        HttpPost httpRequest = new HttpPost(endpoint);
        httpRequest.setConfig(RequestConfig.custom()
                .setResponseTimeout(Timeout.ofMilliseconds(timeout.toMillis()))
                .build());
        httpRequest.setHeader("Authorization", "Bearer " + apiKey);
        httpRequest.setHeader("Content-Type", "application/json");
        httpRequest.setHeader("Accept", "application/json");
        customHeaders.forEach(httpRequest::setHeader);
        httpRequest.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON.withCharset(StandardCharsets.UTF_8)));
        return httpRequest;
    }

    private EmbeddingRequest prepareRequest(EmbeddingRequest request) {
        EmbeddingRequest preparedRequest = request == null ? new EmbeddingRequest() : request.copy();
        if (preparedRequest.getModel() == null || preparedRequest.getModel().isBlank()) {
            preparedRequest.setModel(configuredModel);
        }
        if (preparedRequest.getInputs() == null) {
            preparedRequest.setInputs(List.of());
        }
        if (preparedRequest.getDimensions() == null) {
            preparedRequest.setDimensions(configuredDimensions);
        }
        validateDimensions(preparedRequest.getDimensions());
        return preparedRequest;
    }

    private Map<String, Object> toOpenAiPayload(EmbeddingRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", request.getModel());
        payload.put("input", request.getInputs());
        payload.putAll(request.getOptions());
        if (request.getDimensions() != null) {
            payload.put("dimensions", request.getDimensions());
        }
        return payload;
    }

    private EmbeddingResponse toEmbeddingResponse(JsonNode root) {
        EmbeddingResponse response = new EmbeddingResponse();
        response.setModel(textOrNull(root, "model"));
        response.setUsage(valueOrNull(root, "usage"));

        JsonNode data = root.path("data");
        if (data.isArray()) {
            List<JsonNode> ordered = new ArrayList<>();
            data.forEach(ordered::add);
            ordered.sort((left, right) -> Integer.compare(
                    integerOrDefault(left, "index", 0),
                    integerOrDefault(right, "index", 0)
            ));
            for (JsonNode item : ordered) {
                response.getEmbeddings().add(toVector(item.path("embedding")));
            }
        }
        copyAdditionalProperties(root, response.getAdditionalProperties(), "model", "data", "usage", "object");
        return response;
    }

    private float[] toVector(JsonNode node) {
        if (!node.isArray()) {
            return new float[0];
        }
        float[] vector = new float[node.size()];
        for (int i = 0; i < node.size(); i++) {
            vector[i] = (float) node.get(i).asDouble();
        }
        return vector;
    }

    private void ensureSuccess(int statusCode, String body) {
        if (statusCode < 200 || statusCode >= 300) {
            throw new AiModelException("Embedding model call failed with HTTP " + statusCode + ": " + body);
        }
    }

    private String readResponseBody(ClassicHttpResponse response) throws IOException {
        HttpEntity entity = response.getEntity();
        return entity == null ? "" : readAll(entity.getContent());
    }

    private String readAll(InputStream inputStream) throws IOException {
        try (inputStream) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String textOrNull(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private int integerOrDefault(JsonNode node, String fieldName, int defaultValue) {
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? defaultValue : value.asInt();
    }

    private Object valueOrNull(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() ? null : objectMapper.convertValue(value, Object.class);
    }

    private void copyAdditionalProperties(JsonNode node, Map<String, Object> target, String... knownFields) {
        node.properties().forEach(entry -> {
            if (!isKnownField(entry.getKey(), knownFields)) {
                target.put(entry.getKey(), objectMapper.convertValue(entry.getValue(), Object.class));
            }
        });
    }

    private static boolean isKnownField(String fieldName, String[] knownFields) {
        for (String knownField : knownFields) {
            if (knownField.equals(fieldName)) {
                return true;
            }
        }
        return false;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static Integer validateDimensions(Integer dimensions) {
        if (dimensions != null && dimensions <= 0) {
            throw new IllegalArgumentException("Embedding dimensions must be positive");
        }
        return dimensions;
    }

    private static String trimTrailingSlash(String value) {
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

}
