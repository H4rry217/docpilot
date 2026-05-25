package io.docpilot.config;

import io.docpilot.ai.AiModelRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "docpilot.ai.default-model-id=test",
        "docpilot.ai.models.test.enabled=true",
        "docpilot.ai.models.test.provider=openai-compatible",
        "docpilot.ai.models.test.base-url=http://127.0.0.1:1",
        "docpilot.ai.models.test.api-key=test-key",
        "docpilot.ai.models.test.model=test-model",
        "docpilot.ai.models.disabled.enabled=false"
})
class AiApplicationConfigTest {

    @Autowired
    private AiModelRegistry aiModelRegistry;

    @Test
    void registersEnabledAiModelsFromProperties() {
        assertThat(aiModelRegistry.defaultModelId()).isEqualTo("test");
        assertThat(aiModelRegistry.modelIds()).containsExactly("test");
        assertThat(aiModelRegistry.resolve(null).id()).isEqualTo("test");
    }

}
