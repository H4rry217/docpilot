package io.docpilot.ai.model;

/**
 * Provider-neutral stream event categories.
 */
public enum ChatStreamEventType {

    /**
     * Assistant role or content changed.
     */
    MESSAGE_DELTA,

    /**
     * Assistant reasoning or thinking content changed.
     */
    REASONING_DELTA,

    /**
     * Assistant tool-call payload changed.
     */
    TOOL_CALL_DELTA,

    /**
     * A choice has finished generation.
     */
    COMPLETED,

    /**
     * Token usage information was emitted.
     */
    USAGE,

    /**
     * Provider metadata without a message delta.
     */
    METADATA

}
