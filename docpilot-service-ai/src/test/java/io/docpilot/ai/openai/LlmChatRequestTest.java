package io.docpilot.ai.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmChatRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildsPayloadWithoutJacksonAnnotations() throws IOException {
        LlmChatRequest request = new LlmChatRequest();
        request.setModel("configured-model");
        request.setMessages(List.of(new OpenAiChatMessage("user", "hello")));
        request.setTemperature(0.2);
        request.setTopP(0.9);
        request.setMaxTokens(128);
        request.setStream(false);
        request.setResponseFormat(LlmJsonSchema.builder()
                .name("message_fields")
                .prop(LlmJsonSchema.stringProp("answer"))
                .build());
        request.setAdditionalProperty("custom_flag", true);

        JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(request.toPayload()));

        assertThat(root.path("model").asText()).isEqualTo("configured-model");
        assertThat(root.path("messages").get(0).path("role").asText()).isEqualTo("user");
        assertThat(root.path("temperature").asDouble()).isEqualTo(0.2);
        assertThat(root.path("top_p").asDouble()).isEqualTo(0.9);
        assertThat(root.path("max_tokens").asInt()).isEqualTo(128);
        assertThat(root.path("stream").asBoolean()).isFalse();
        assertThat(root.path("response_format").path("json_schema").path("name").asText()).isEqualTo("message_fields");
        assertThat(root.path("custom_flag").asBoolean()).isTrue();
        assertThat(root.has("topP")).isFalse();
        assertThat(root.has("maxTokens")).isFalse();
        assertThat(root.has("responseFormat")).isFalse();
    }

    @Test
    void skipsNullPayloadFieldsAndCanRemoveAdditionalProperties() {
        LlmChatRequest request = new LlmChatRequest();
        request.setAdditionalProperty("custom_flag", true);

        request.setAdditionalProperty("custom_flag", null);

        assertThat(request.toPayload()).containsEntry("messages", List.of());
        assertThat(request.toPayload()).doesNotContainKeys(
                "model",
                "temperature",
                "top_p",
                "max_tokens",
                "stream",
                "response_format",
                "custom_flag"
        );
    }

}
