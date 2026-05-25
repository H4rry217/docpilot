package io.docpilot.ai;

import io.docpilot.ai.openai.OpenAiChatCompletionRequest;
import io.docpilot.ai.openai.OpenAiChatCompletionResponse;
import reactor.core.publisher.Flux;

public interface AiChatModel {

    String id();

    OpenAiChatCompletionResponse chat(OpenAiChatCompletionRequest request);

    Flux<OpenAiChatCompletionResponse> stream(OpenAiChatCompletionRequest request);

}
