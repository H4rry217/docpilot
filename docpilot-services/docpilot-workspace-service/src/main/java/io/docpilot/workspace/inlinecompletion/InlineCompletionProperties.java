package io.docpilot.workspace.inlinecompletion;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumMap;
import java.util.Map;

/**
 * Inline completion model and generation limits.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "docpilot.inline-completion")
public class InlineCompletionProperties {

    private boolean enabled = true;

    private String modelId;

    private double temperature = 0.2D;

    private Map<InlineCompletionShape, Integer> maxOutputTokens = defaultMaxOutputTokens();

    /**
     * Prompt templates used by inline completion model calls.
     */
    private PromptProperties prompts = new PromptProperties();

    public int maxOutputTokens(InlineCompletionShape shape) {
        InlineCompletionShape effectiveShape = shape == null ? InlineCompletionShape.SENTENCE : shape;
        Integer configured = maxOutputTokens == null ? null : maxOutputTokens.get(effectiveShape);
        return configured == null || configured <= 0 ? defaultMaxOutputTokens().get(effectiveShape) : configured;
    }

    private static Map<InlineCompletionShape, Integer> defaultMaxOutputTokens() {
        EnumMap<InlineCompletionShape, Integer> defaults = new EnumMap<>(InlineCompletionShape.class);
        defaults.put(InlineCompletionShape.SHORT, 32);
        defaults.put(InlineCompletionShape.SENTENCE, 64);
        defaults.put(InlineCompletionShape.PARAGRAPH, 160);
        defaults.put(InlineCompletionShape.LIST_ITEM, 80);
        defaults.put(InlineCompletionShape.TABLE_CELL, 32);
        defaults.put(InlineCompletionShape.CODE_LINE, 96);
        return defaults;
    }

    @Getter
    @Setter
    public static class PromptProperties {

        /**
         * Prompt templates for the complete inline-completion request.
         */
        private CompletePromptProperties complete = new CompletePromptProperties();

    }

    @Getter
    @Setter
    public static class CompletePromptProperties {

        /**
         * System prompt template. Supports {{candidateCount}}, {{candidateTokenLimit}}, and {{shape}}.
         */
        private String system;

        /**
         * User prompt template. Supports editor context, retrieval context, and generation constraint placeholders.
         */
        private String user;

    }

}
