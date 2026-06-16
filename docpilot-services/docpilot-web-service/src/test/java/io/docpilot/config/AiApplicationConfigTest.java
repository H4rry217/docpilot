package io.docpilot.config;

import io.docpilot.ai.AiEmbeddingRegistry;
import io.docpilot.ai.AiModelRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "docpilot.workspace.mongo.init-indexes=false",
        "docpilot.knowledge.index.mode=direct",
        "docpilot.user-settings.cache.enabled=false",
        "docpilot.ai.default-model-id=test",
        "docpilot.ai.models.test.enabled=true",
        "docpilot.ai.models.test.provider=openai-compatible",
        "docpilot.ai.models.test.base-url=http://127.0.0.1:1",
        "docpilot.ai.models.test.api-key=test-key",
        "docpilot.ai.models.test.model=test-model",
        "docpilot.ai.models.test.display-name=Test Model",
        "docpilot.ai.models.test.context-window-tokens=128000",
        "docpilot.ai.models.test.max-output-tokens=8192",
        "docpilot.ai.models.test.metadata.tier=fast",
        "docpilot.ai.models.disabled.enabled=false",
        "docpilot.ai.default-embedding-model-id=embedding",
        "docpilot.ai.embeddings.embedding.enabled=true",
        "docpilot.ai.embeddings.embedding.provider=openai-compatible",
        "docpilot.ai.embeddings.embedding.base-url=http://127.0.0.1:1",
        "docpilot.ai.embeddings.embedding.api-key=test-key",
        "docpilot.ai.embeddings.embedding.model=embedding-model",
        "docpilot.ai.embeddings.embedding.dimensions=1024"
})
class AiApplicationConfigTest {

    @Autowired
    private AiModelRegistry aiModelRegistry;

    @Autowired
    private AiEmbeddingRegistry aiEmbeddingRegistry;

    @Test
    void registersEnabledAiModelsFromConfig() {
        assertThat(aiModelRegistry.defaultModelId()).isEqualTo("test");
        assertThat(aiModelRegistry.modelIds()).containsExactly("test");
        assertThat(aiModelRegistry.resolve(null).id()).isEqualTo("test");
        assertThat(aiModelRegistry.resolveMetadata(null).provider()).isEqualTo("openai-compatible");
        assertThat(aiModelRegistry.resolveMetadata(null).modelName()).isEqualTo("test-model");
        assertThat(aiModelRegistry.resolveMetadata(null).displayName()).isEqualTo("Test Model");
        assertThat(aiModelRegistry.resolveMetadata(null).contextWindowTokens()).isEqualTo(128000);
        assertThat(aiModelRegistry.resolveMetadata(null).maxOutputTokens()).isEqualTo(8192);
        assertThat(aiModelRegistry.resolveMetadata(null).additionalProperties()).containsEntry("tier", "fast");
    }

    @Test
    void registersEnabledEmbeddingModelsFromConfig() {
        assertThat(aiEmbeddingRegistry.defaultModelId()).isEqualTo("embedding");
        assertThat(aiEmbeddingRegistry.modelIds()).containsExactly("embedding");
        assertThat(aiEmbeddingRegistry.resolve(null).metadata().modelName()).isEqualTo("embedding-model");
        assertThat(aiEmbeddingRegistry.resolve(null).metadata().additionalProperties())
                .containsEntry("dimensions", 1024);
    }

}
