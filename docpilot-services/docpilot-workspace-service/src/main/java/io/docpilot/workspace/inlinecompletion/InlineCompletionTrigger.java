package io.docpilot.workspace.inlinecompletion;

/**
 * Client-side reason that started an inline completion request.
 */
public enum InlineCompletionTrigger {

    IDLE,
    MANUAL;

    public static InlineCompletionTrigger parse(String value) {
        if (value == null || value.isBlank()) {
            return IDLE;
        }
        try {
            return InlineCompletionTrigger.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported inline completion trigger: " + value, exception);
        }
    }

}
