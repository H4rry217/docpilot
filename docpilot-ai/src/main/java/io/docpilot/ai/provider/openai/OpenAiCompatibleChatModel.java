package io.docpilot.ai.provider.openai;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.docpilot.ai.AiChatModel;
import io.docpilot.ai.AiModelException;
import io.docpilot.ai.AiModelMetadata;
import io.docpilot.ai.model.ChatChoice;
import io.docpilot.ai.model.ChatDelta;
import io.docpilot.ai.model.ChatMessage;
import io.docpilot.ai.model.ChatRequest;
import io.docpilot.ai.model.ChatResponse;
import io.docpilot.ai.model.ChatResponseFormat;
import io.docpilot.ai.model.ChatStreamEvent;
import io.docpilot.ai.model.ChatStreamEventType;
import io.docpilot.ai.model.ChatUsage;
import io.docpilot.ai.model.JsonSchemaResponseFormat;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * {@link AiChatModel} implementation for providers that expose OpenAI-compatible chat completions.
 *
 * <p>The adapter keeps OpenAI wire DTOs private to this package and translates them to DocPilot's
 * provider-neutral chat models at the boundary.
 */
public class OpenAiCompatibleChatModel implements AiChatModel {

    private static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    private final String id;
    private final AiModelMetadata metadata;
    private final URI endpoint;
    private final String apiKey;
    private final String configuredModel;
    private final Map<String, String> customHeaders;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * Creates an adapter with the default JDK HTTP client and object mapper.
     */
    public OpenAiCompatibleChatModel(String id,
                                     String baseUrl,
                                     String apiKey,
                                     String configuredModel,
                                     Duration timeout) {
        this(defaultMetadata(id, configuredModel), baseUrl, apiKey, configuredModel, timeout);
    }

    public OpenAiCompatibleChatModel(String id,
                                     String baseUrl,
                                     String apiKey,
                                     String configuredModel,
                                     Duration timeout,
                                     Map<String, String> customHeaders) {
        this(defaultMetadata(id, configuredModel), baseUrl, apiKey, configuredModel, timeout, customHeaders);
    }

    public OpenAiCompatibleChatModel(AiModelMetadata metadata,
                                     String baseUrl,
                                     String apiKey,
                                     String configuredModel,
                                     Duration timeout) {
        this(metadata, baseUrl, apiKey, configuredModel, timeout, Map.of());
    }

    public OpenAiCompatibleChatModel(AiModelMetadata metadata,
                                     String baseUrl,
                                     String apiKey,
                                     String configuredModel,
                                     Duration timeout,
                                     Map<String, String> customHeaders) {
        this(metadata, baseUrl, apiKey, configuredModel, timeout, customHeaders,
                HttpClient.newHttpClient(), new ObjectMapper());
    }

    /**
     * Creates an adapter with injectable transport dependencies for tests and custom runtimes.
     */
    public OpenAiCompatibleChatModel(String id,
                                     String baseUrl,
                                     String apiKey,
                                     String configuredModel,
                                     Duration timeout,
                                     HttpClient httpClient,
                                     ObjectMapper objectMapper) {
        this(defaultMetadata(id, configuredModel), baseUrl, apiKey, configuredModel, timeout, httpClient, objectMapper);
    }

    public OpenAiCompatibleChatModel(String id,
                                     String baseUrl,
                                     String apiKey,
                                     String configuredModel,
                                     Duration timeout,
                                     Map<String, String> customHeaders,
                                     HttpClient httpClient,
                                     ObjectMapper objectMapper) {
        this(defaultMetadata(id, configuredModel), baseUrl, apiKey, configuredModel, timeout,
                customHeaders, httpClient, objectMapper);
    }

    public OpenAiCompatibleChatModel(AiModelMetadata metadata,
                                     String baseUrl,
                                     String apiKey,
                                     String configuredModel,
                                     Duration timeout,
                                     HttpClient httpClient,
                                     ObjectMapper objectMapper) {
        this(metadata, baseUrl, apiKey, configuredModel, timeout, Map.of(), httpClient, objectMapper);
    }

    public OpenAiCompatibleChatModel(AiModelMetadata metadata,
                                     String baseUrl,
                                     String apiKey,
                                     String configuredModel,
                                     Duration timeout,
                                     Map<String, String> customHeaders,
                                     HttpClient httpClient,
                                     ObjectMapper objectMapper) {
        this.metadata = Objects.requireNonNull(metadata, "AI model metadata must not be null");
        this.id = metadata.id();
        this.endpoint = URI.create(trimTrailingSlash(requireText(baseUrl, "OpenAI-compatible baseUrl is required"))
                + CHAT_COMPLETIONS_PATH);
        this.apiKey = requireText(apiKey, "OpenAI-compatible apiKey is required for model: " + id);
        this.configuredModel = requireText(configuredModel, "OpenAI-compatible model is required for model: " + id);
        this.customHeaders = customHeaders == null ? Map.of() : Map.copyOf(customHeaders);
        this.timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
        this.httpClient = httpClient == null ? HttpClient.newHttpClient() : httpClient;
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
    public ChatResponse chat(ChatRequest request) {
        ChatRequest preparedRequest = prepareRequest(request);
        HttpRequest httpRequest = buildRequest(preparedRequest, false, "application/json");
        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            ensureSuccess(response.statusCode(), response.body());
            return toChatResponse(readOpenAiResponse(response.body()));
        } catch (IOException | JacksonException exception) {
            throw new AiModelException("Failed to call AI model: " + id, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiModelException("AI model call was interrupted: " + id, exception);
        }
    }

    @Override
    public Flux<ChatStreamEvent> stream(ChatRequest request) {
        return Flux.defer(() -> {
            ChatRequest preparedRequest = prepareRequest(request);
            HttpRequest httpRequest = buildRequest(preparedRequest, true, "text/event-stream");
            try {
                HttpResponse<InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    String body = readAll(response.body());
                    return Flux.error(new AiModelException("AI model stream failed with HTTP "
                            + response.statusCode() + ": " + body));
                }
                return decodeServerSentEvents(response.body()).flatMapIterable(this::toStreamEvents);
            } catch (IOException exception) {
                return Flux.error(new AiModelException("Failed to stream AI model: " + id, exception));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return Flux.error(new AiModelException("AI model stream was interrupted: " + id, exception));
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Applies adapter defaults without mutating the caller's request object.
     */
    private ChatRequest prepareRequest(ChatRequest request) {
        ChatRequest preparedRequest = request == null ? new ChatRequest() : request.copy();
        if (preparedRequest.getModel() == null || preparedRequest.getModel().isBlank()) {
            preparedRequest.setModel(configuredModel);
        }
        return preparedRequest;
    }

    /**
     * Builds the OpenAI-compatible HTTP request body and headers.
     */
    private HttpRequest buildRequest(ChatRequest request, boolean stream, String accept) {
        try {
            String body = objectMapper.writeValueAsString(toOpenAiPayload(request, stream));
            HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", accept);
            customHeaders.forEach(builder::setHeader);
            return builder.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        } catch (JacksonException exception) {
            throw new AiModelException("Failed to serialize AI request for model: " + id, exception);
        }
    }

    /**
     * Converts the provider-neutral request into OpenAI-compatible JSON field names.
     */
    private Map<String, Object> toOpenAiPayload(ChatRequest request, boolean stream) {
        Map<String, Object> payload = new LinkedHashMap<>();
        putIfNotNull(payload, "model", request.getModel());
        putIfNotNull(payload, "messages", toOpenAiMessages(request.getMessages()));
        putIfNotNull(payload, "temperature", request.getTemperature());
        putIfNotNull(payload, "top_p", request.getTopP());
        putIfNotNull(payload, "max_tokens", request.getMaxOutputTokens());
        payload.put("stream", stream);
        putIfNotNull(payload, "response_format", toOpenAiResponseFormat(request.getResponseFormat()));
        payload.putAll(request.getOptions());
        return payload;
    }

    /**
     * Converts DocPilot chat messages into OpenAI-compatible message maps.
     */
    private List<Map<String, Object>> toOpenAiMessages(List<ChatMessage> messages) {
        if (messages == null) {
            return null;
        }
        List<Map<String, Object>> payloadMessages = new ArrayList<>();
        for (ChatMessage message : messages) {
            if (message == null) {
                continue;
            }
            Map<String, Object> payloadMessage = new LinkedHashMap<>();
            putIfNotNull(payloadMessage, "role", message.getRole());
            putIfNotNull(payloadMessage, "content", message.getContent());
            putIfNotNull(payloadMessage, "name", message.getName());
            putIfNotNull(payloadMessage, "tool_call_id", message.getToolCallId());
            putIfNotNull(payloadMessage, "tool_calls", message.getToolCalls());
            putIfNotNull(payloadMessage, "reasoning_content", message.getReasoningContent());
            payloadMessage.putAll(message.getAdditionalProperties());
            payloadMessages.add(payloadMessage);
        }
        return payloadMessages;
    }

    /**
     * Converts supported response formats into OpenAI-compatible response_format payloads.
     */
    private Object toOpenAiResponseFormat(ChatResponseFormat responseFormat) {
        if (responseFormat == null) {
            return null;
        }
        if (!(responseFormat instanceof JsonSchemaResponseFormat jsonSchema)) {
            throw new IllegalArgumentException("Unsupported chat response format: "
                    + responseFormat.getClass().getName());
        }
        Map<String, Object> jsonSchemaDefinition = new LinkedHashMap<>();
        putIfNotNull(jsonSchemaDefinition, "name", jsonSchema.getName());
        putIfNotNull(jsonSchemaDefinition, "strict", jsonSchema.getStrict());
        putIfNotNull(jsonSchemaDefinition, "schema", jsonSchema.getSchema());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "json_schema");
        payload.put("json_schema", jsonSchemaDefinition);
        return payload;
    }

    /**
     * Decodes data-only server-sent events from OpenAI-compatible streaming responses.
     */
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
                            sink.next(readOpenAiResponse(data));
                            return false;
                        }
                    } catch (IOException | JacksonException exception) {
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

    /**
     * Parses OpenAI-compatible JSON explicitly so DTOs do not need Jackson field annotations.
     */
    private OpenAiChatCompletionResponse readOpenAiResponse(String json) throws JacksonException {
        return toOpenAiResponse(objectMapper.readTree(json));
    }

    private OpenAiChatCompletionResponse toOpenAiResponse(JsonNode root) {
        OpenAiChatCompletionResponse response = new OpenAiChatCompletionResponse();
        response.setId(textOrNull(root, "id"));
        response.setObject(textOrNull(root, "object"));
        response.setCreated(longOrNull(root, "created"));
        response.setModel(textOrNull(root, "model"));
        response.setSystemFingerprint(textOrNull(root, "system_fingerprint"));
        response.setUsage(toOpenAiUsage(root.path("usage")));

        JsonNode choices = root.path("choices");
        if (choices.isArray()) {
            for (JsonNode choice : choices) {
                response.getChoices().add(toOpenAiChoice(choice));
            }
        }
        copyAdditionalProperties(root, response.getAdditionalProperties(),
                "id", "object", "created", "model", "system_fingerprint", "choices", "usage");
        return response;
    }

    private OpenAiChoice toOpenAiChoice(JsonNode node) {
        OpenAiChoice choice = new OpenAiChoice();
        choice.setIndex(integerOrNull(node, "index"));
        choice.setMessage(toOpenAiMessage(node.path("message")));
        choice.setDelta(toOpenAiDelta(node.path("delta")));
        choice.setFinishReason(textOrNull(node, "finish_reason"));
        choice.setLogprobs(valueOrNull(node, "logprobs"));
        copyAdditionalProperties(node, choice.getAdditionalProperties(),
                "index", "message", "delta", "finish_reason", "logprobs");
        return choice;
    }

    private OpenAiChatMessage toOpenAiMessage(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        OpenAiChatMessage message = new OpenAiChatMessage();
        message.setRole(textOrNull(node, "role"));
        message.setContent(valueOrNull(node, "content"));
        message.setName(textOrNull(node, "name"));
        message.setToolCallId(textOrNull(node, "tool_call_id"));
        message.setToolCalls(valueOrNull(node, "tool_calls"));
        message.setReasoningContent(valueOrNull(node, "reasoning_content"));
        copyAdditionalProperties(node, message.getAdditionalProperties(),
                "role", "content", "name", "tool_call_id", "tool_calls", "reasoning_content");
        return message;
    }

    private OpenAiChatDelta toOpenAiDelta(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        OpenAiChatDelta delta = new OpenAiChatDelta();
        delta.setRole(textOrNull(node, "role"));
        delta.setContent(valueOrNull(node, "content"));
        delta.setToolCalls(valueOrNull(node, "tool_calls"));
        delta.setReasoningContent(valueOrNull(node, "reasoning_content"));
        copyAdditionalProperties(node, delta.getAdditionalProperties(), "role", "content", "tool_calls", "reasoning_content");
        return delta;
    }

    private OpenAiUsage toOpenAiUsage(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        OpenAiUsage usage = new OpenAiUsage();
        usage.setPromptTokens(integerOrNull(node, "prompt_tokens"));
        usage.setCompletionTokens(integerOrNull(node, "completion_tokens"));
        usage.setTotalTokens(integerOrNull(node, "total_tokens"));
        usage.setPromptTokensDetails(valueOrNull(node, "prompt_tokens_details"));
        usage.setCompletionTokensDetails(valueOrNull(node, "completion_tokens_details"));
        copyAdditionalProperties(node, usage.getAdditionalProperties(),
                "prompt_tokens", "completion_tokens", "total_tokens",
                "prompt_tokens_details", "completion_tokens_details");
        return usage;
    }

    /**
     * Maps a non-streaming provider response to DocPilot's neutral response model.
     */
    private ChatResponse toChatResponse(OpenAiChatCompletionResponse response) {
        ChatResponse chatResponse = new ChatResponse();
        chatResponse.setId(response.getId());
        chatResponse.setCreatedEpochSecond(response.getCreated());
        chatResponse.setModel(response.getModel());
        chatResponse.setSystemFingerprint(response.getSystemFingerprint());
        chatResponse.setUsage(toChatUsage(response.getUsage()));
        if (response.getObject() != null) {
            chatResponse.setAdditionalProperty("object", response.getObject());
        }
        chatResponse.getAdditionalProperties().putAll(response.getAdditionalProperties());
        List<ChatChoice> choices = new ArrayList<>();
        if (response.getChoices() != null) {
            for (OpenAiChoice choice : response.getChoices()) {
                choices.add(toChatChoice(choice));
            }
        }
        chatResponse.setChoices(choices);
        return chatResponse;
    }

    private ChatChoice toChatChoice(OpenAiChoice choice) {
        ChatChoice chatChoice = new ChatChoice();
        chatChoice.setIndex(choice.getIndex());
        chatChoice.setMessage(toChatMessage(choice.getMessage()));
        chatChoice.setFinishReason(choice.getFinishReason());
        chatChoice.setLogprobs(choice.getLogprobs());
        chatChoice.getAdditionalProperties().putAll(choice.getAdditionalProperties());
        return chatChoice;
    }

    private ChatMessage toChatMessage(OpenAiChatMessage message) {
        if (message == null) {
            return null;
        }
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setRole(message.getRole());
        chatMessage.setContent(message.getContent());
        chatMessage.setName(message.getName());
        chatMessage.setToolCallId(message.getToolCallId());
        chatMessage.setToolCalls(message.getToolCalls());
        chatMessage.setReasoningContent(message.getReasoningContent());
        chatMessage.getAdditionalProperties().putAll(message.getAdditionalProperties());
        return chatMessage;
    }

    /**
     * Maps provider stream chunks to one or more semantic stream events.
     */
    private List<ChatStreamEvent> toStreamEvents(OpenAiChatCompletionResponse response) {
        List<ChatStreamEvent> events = new ArrayList<>();
        ChatUsage usage = toChatUsage(response.getUsage());
        if (response.getChoices() != null) {
            for (OpenAiChoice choice : response.getChoices()) {
                events.add(toStreamEvent(response, choice, usage));
            }
        }
        if (events.isEmpty() && usage != null) {
            ChatStreamEvent usageEvent = baseStreamEvent(response, usage);
            usageEvent.setType(ChatStreamEventType.USAGE);
            events.add(usageEvent);
        }
        if (events.isEmpty()) {
            ChatStreamEvent metadataEvent = baseStreamEvent(response, null);
            metadataEvent.setType(ChatStreamEventType.METADATA);
            events.add(metadataEvent);
        }
        return events;
    }

    private ChatStreamEvent toStreamEvent(OpenAiChatCompletionResponse response,
                                          OpenAiChoice choice,
                                          ChatUsage usage) {
        ChatStreamEvent event = baseStreamEvent(response, usage);
        event.setChoiceIndex(choice.getIndex());
        event.setDelta(toChatDelta(choice.getDelta()));
        event.setFinishReason(choice.getFinishReason());
        event.getAdditionalProperties().putAll(choice.getAdditionalProperties());
        event.setType(resolveStreamEventType(event));
        return event;
    }

    private ChatStreamEvent baseStreamEvent(OpenAiChatCompletionResponse response, ChatUsage usage) {
        ChatStreamEvent event = new ChatStreamEvent();
        event.setId(response.getId());
        event.setCreatedEpochSecond(response.getCreated());
        event.setModel(response.getModel());
        event.setSystemFingerprint(response.getSystemFingerprint());
        event.setUsage(usage);
        if (response.getObject() != null) {
            event.setAdditionalProperty("object", response.getObject());
        }
        event.getAdditionalProperties().putAll(response.getAdditionalProperties());
        return event;
    }

    private ChatStreamEventType resolveStreamEventType(ChatStreamEvent event) {
        ChatDelta delta = event.getDelta();
        if (delta != null && delta.getToolCalls() != null) {
            return ChatStreamEventType.TOOL_CALL_DELTA;
        }
        if (delta != null && hasValue(delta.getReasoningContent())) {
            return ChatStreamEventType.REASONING_DELTA;
        }
        if (delta != null && (delta.getRole() != null || hasValue(delta.getContent()))) {
            return ChatStreamEventType.MESSAGE_DELTA;
        }
        if (event.getFinishReason() != null) {
            return ChatStreamEventType.COMPLETED;
        }
        if (event.getUsage() != null) {
            return ChatStreamEventType.USAGE;
        }
        return ChatStreamEventType.METADATA;
    }

    private ChatDelta toChatDelta(OpenAiChatDelta delta) {
        if (delta == null) {
            return null;
        }
        ChatDelta chatDelta = new ChatDelta();
        chatDelta.setRole(delta.getRole());
        chatDelta.setContent(delta.getContent());
        chatDelta.setToolCalls(delta.getToolCalls());
        chatDelta.setReasoningContent(delta.getReasoningContent());
        chatDelta.getAdditionalProperties().putAll(delta.getAdditionalProperties());
        return chatDelta;
    }

    private ChatUsage toChatUsage(OpenAiUsage usage) {
        if (usage == null) {
            return null;
        }
        ChatUsage chatUsage = new ChatUsage();
        chatUsage.setInputTokens(usage.getPromptTokens());
        chatUsage.setOutputTokens(usage.getCompletionTokens());
        chatUsage.setTotalTokens(usage.getTotalTokens());
        chatUsage.setAdditionalProperty("prompt_tokens_details", usage.getPromptTokensDetails());
        chatUsage.setAdditionalProperty("completion_tokens_details", usage.getCompletionTokensDetails());
        chatUsage.getAdditionalProperties().putAll(usage.getAdditionalProperties());
        return chatUsage;
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

    private static void putIfNotNull(Map<String, Object> payload, String name, Object value) {
        if (value != null) {
            payload.put(name, value);
        }
    }

    private static boolean hasValue(Object value) {
        return value != null && (!(value instanceof String text) || !text.isEmpty());
    }

    private static AiModelMetadata defaultMetadata(String id, String configuredModel) {
        return AiModelMetadata.builder()
                .id(id)
                .provider("openai-compatible")
                .modelName(configuredModel)
                .build();
    }

    private String textOrNull(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private Integer integerOrNull(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? null : value.asInt();
    }

    private Long longOrNull(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? null : value.asLong();
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

}
