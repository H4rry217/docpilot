package io.docpilot.ai.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatRequestTest {

    @Test
    void copiesRequestWithoutSharingMutableCollections() {
        ChatRequest request = new ChatRequest();
        request.setModel("configured-model");
        request.setMessages(List.of(new ChatMessage("user", "hello")));
        request.setTemperature(0.2);
        request.setTopP(0.9);
        request.setMaxOutputTokens(128);
        request.setResponseFormat(JsonSchemaResponseFormat.of("message_fields", JsonSchema.builder()
                .prop(JsonSchema.stringProp("answer"))
                .build()));
        request.setOption("custom_flag", true);

        ChatRequest copy = request.copy();
        request.setOption("custom_flag", false);

        assertThat(copy.getModel()).isEqualTo("configured-model");
        assertThat(copy.getMessages()).hasSize(1);
        assertThat(copy.getMessages().getFirst().getRole()).isEqualTo("user");
        assertThat(copy.getTemperature()).isEqualTo(0.2);
        assertThat(copy.getTopP()).isEqualTo(0.9);
        assertThat(copy.getMaxOutputTokens()).isEqualTo(128);
        assertThat(copy.getResponseFormat()).isInstanceOf(JsonSchemaResponseFormat.class);
        assertThat(copy.getOptions()).containsEntry("custom_flag", true);
    }

    @Test
    void canRemoveProviderOptions() {
        ChatRequest request = new ChatRequest();
        request.setOption("custom_flag", true);

        request.setOption("custom_flag", null);

        assertThat(request.getOptions()).doesNotContainKey("custom_flag");
    }

}
