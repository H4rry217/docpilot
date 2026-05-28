package io.docpilot.ai.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provider-neutral chat message.
 *
 * <p>{@code content} is intentionally an {@link Object} so providers can support plain text,
 * multimodal parts, or structured message content without changing this core model.
 */
@Getter
@Setter
@NoArgsConstructor
public class ChatMessage implements AdditionalPropertiesCarrier {

    /**
     * Conversation role, such as system, user, assistant, or tool.
     */
    private String role;

    /**
     * Message body. Most callers use a String; provider adapters may also accept structured parts.
     */
    private Object content;

    /**
     * Optional participant name supported by some chat providers.
     */
    private String name;

    /**
     * Id of the tool call this message responds to.
     */
    private String toolCallId;

    /**
     * Provider-neutral holder for assistant tool call payloads.
     */
    private Object toolCalls;

    /**
     * Reasoning text exposed by providers that support thinking-mode responses.
     */
    private Object reasoningContent;

    /**
     * Extension fields preserved for providers with extra message properties.
     */
    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

    public ChatMessage(String role, Object content) {
        this.role = role;
        this.content = content;
    }

}
