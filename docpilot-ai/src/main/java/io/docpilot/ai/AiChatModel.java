package io.docpilot.ai;

import io.docpilot.ai.model.ChatRequest;
import io.docpilot.ai.model.ChatResponse;
import io.docpilot.ai.model.ChatStreamEvent;
import reactor.core.publisher.Flux;

/**
 * Provider-neutral chat model boundary used by DocPilot services.
 *
 * <p>Implementations may call OpenAI-compatible APIs, local models, or other providers, but
 * callers should only depend on the stable {@code io.docpilot.ai.model} request and response
 * types exposed here.
 */
public interface AiChatModel {

    /**
     * Stable registry id for this configured model.
     */
    String id();

    /**
     * Metadata describing configured model identity and known capability limits.
     */
    AiModelMetadata metadata();

    /**
     * Sends a non-streaming chat request and returns the complete assistant response.
     */
    ChatResponse chat(ChatRequest request);

    /**
     * Sends a streaming chat request and emits semantic stream events rather than provider chunks.
     */
    Flux<ChatStreamEvent> stream(ChatRequest request);

}
