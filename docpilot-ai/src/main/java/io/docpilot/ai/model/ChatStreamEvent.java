package io.docpilot.ai.model;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Semantic stream event emitted by {@link io.docpilot.ai.AiChatModel#stream(ChatRequest)}.
 *
 * <p>Adapters convert provider-specific transport chunks into these events so callers can handle
 * message deltas, tool-call deltas, usage, and completion in a provider-neutral way.
 */
@Getter
@Setter
public class ChatStreamEvent implements AdditionalPropertiesCarrier {

    /**
     * Semantic kind of this stream event.
     */
    private ChatStreamEventType type = ChatStreamEventType.MESSAGE_DELTA;

    /**
     * Provider response or stream id when available.
     */
    private String id;

    /**
     * Provider creation timestamp in epoch seconds.
     */
    private Long createdEpochSecond;

    /**
     * Provider model name used for the event.
     */
    private String model;

    /**
     * Provider backend fingerprint when supplied for diagnostics.
     */
    private String systemFingerprint;

    /**
     * Choice index this event belongs to.
     */
    private Integer choiceIndex;

    /**
     * Incremental message or tool-call content.
     */
    private ChatDelta delta;

    /**
     * Provider finish reason when this event closes a choice.
     */
    private String finishReason;

    /**
     * Usage reported during or at the end of a stream.
     */
    private ChatUsage usage;

    /**
     * Extension fields preserved from provider stream events.
     */
    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

}
