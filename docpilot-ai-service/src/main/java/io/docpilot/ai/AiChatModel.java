package io.docpilot.ai;

import io.docpilot.ai.openai.LlmChatRequest;
import io.docpilot.ai.openai.OpenAiChatCompletionResponse;
import reactor.core.publisher.Flux;

public interface AiChatModel {

    String id();

    OpenAiChatCompletionResponse chat(LlmChatRequest request);

    Flux<OpenAiChatCompletionResponse> stream(LlmChatRequest request);

}
