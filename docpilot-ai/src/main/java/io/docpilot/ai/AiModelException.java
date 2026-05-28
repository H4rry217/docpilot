package io.docpilot.ai;

/**
 * Runtime exception used for provider call, serialization, and stream parsing failures.
 */
public class AiModelException extends RuntimeException {

    public AiModelException(String message) {
        super(message);
    }

    public AiModelException(String message, Throwable cause) {
        super(message, cause);
    }

}
