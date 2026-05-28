package io.docpilot.ai.provider.openai;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wire DTO for OpenAI-compatible chat messages.
 */
@Getter
@Setter
@NoArgsConstructor
public class OpenAiChatMessage {

    private String role;
    private Object content;
    private String name;
    private String toolCallId;
    private Object toolCalls;
    private Object reasoningContent;

    /**
     * Captures provider extension fields without blocking deserialization.
     */
    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

    public OpenAiChatMessage(String role, Object content) {
        this.role = role;
        this.content = content;
    }

}
