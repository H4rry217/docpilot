package io.docpilot.ai;

import io.docpilot.ai.model.ChatRequest;
import io.docpilot.ai.model.ChatResponse;
import io.docpilot.ai.model.ChatStreamEvent;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiModelRegistryTest {

    @Test
    void resolvesDefaultAndNamedModels() {
        AiChatModel defaultModel = new StubAiChatModel("default");
        AiChatModel codingModel = new StubAiChatModel("coding");

        AiModelRegistry registry = new AiModelRegistry("default", List.of(defaultModel, codingModel));

        assertThat(registry.resolve(null)).isSameAs(defaultModel);
        assertThat(registry.resolve("coding")).isSameAs(codingModel);
        assertThat(registry.modelIds()).containsExactly("default", "coding");
        assertThat(registry.resolveMetadata("coding").modelName()).isEqualTo("coding-provider-model");
        assertThat(registry.metadataByModelId()).containsOnlyKeys("default", "coding");
    }

    @Test
    void reportsUnknownModelsClearly() {
        AiModelRegistry registry = new AiModelRegistry("default", List.of(new StubAiChatModel("default")));

        assertThatThrownBy(() -> registry.resolve("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AI model is not configured or enabled: missing");
    }

    @Test
    void reportsMissingDefaultClearly() {
        AiModelRegistry registry = new AiModelRegistry(null, List.of());

        assertThatThrownBy(() -> registry.resolve(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no default model is configured");
    }

    private record StubAiChatModel(String id) implements AiChatModel {

        @Override
        public AiModelMetadata metadata() {
            return AiModelMetadata.builder()
                    .id(id)
                    .provider("test")
                    .modelName(id + "-provider-model")
                    .contextWindowTokens(128000)
                    .maxOutputTokens(8192)
                    .build();
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            return new ChatResponse();
        }

        @Override
        public Flux<ChatStreamEvent> stream(ChatRequest request) {
            return Flux.empty();
        }

    }

}
