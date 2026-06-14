package io.docpilot.workspace.knowledge;

import io.docpilot.ai.AiModelRegistry;
import io.docpilot.ai.model.ChatChoice;
import io.docpilot.ai.model.ChatMessage;
import io.docpilot.ai.model.ChatRequest;
import io.docpilot.ai.model.ChatResponse;
import io.docpilot.workspace.knowledge.config.KnowledgeProperties;
import io.docpilot.workspace.knowledge.model.KnowledgeChunkDraft;
import io.docpilot.workspace.knowledge.model.KnowledgeSectionDraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * Chat-model backed section summarizer.
 */
public class AiKnowledgeSummaryService implements KnowledgeSummaryService {

    /**
     * Logger for section summary model call lifecycle events.
     */
    private static final Logger log = LoggerFactory.getLogger(AiKnowledgeSummaryService.class);

    /**
     * Registry used to resolve the configured chat model for section summaries.
     */
    private final AiModelRegistry aiModelRegistry;

    /**
     * Knowledge summary model id and token/input limits.
     */
    private final KnowledgeProperties properties;

    /**
     * Executor used for concurrent section summary API calls.
     */
    private final Executor summaryExecutor;

    public AiKnowledgeSummaryService(AiModelRegistry aiModelRegistry, KnowledgeProperties properties) {
        this(aiModelRegistry, properties, Runnable::run);
    }

    public AiKnowledgeSummaryService(AiModelRegistry aiModelRegistry,
                                     KnowledgeProperties properties,
                                     Executor summaryExecutor) {
        this.aiModelRegistry = aiModelRegistry;
        this.properties = properties;
        this.summaryExecutor = summaryExecutor == null ? Runnable::run : summaryExecutor;
    }

    @Override
    public List<KnowledgeChunkDraft> summarize(List<KnowledgeSectionDraft> sections) {
        // No selected complex sections means no generated summary chunks.
        if (sections == null || sections.isEmpty()) {
            return List.of();
        }

        List<KnowledgeSectionDraft> candidates = new ArrayList<>();
        for (KnowledgeSectionDraft section : sections) {
            // Empty sections are intentionally skipped so summaries never invent missing source facts.
            if (section == null || !section.hasContent()) {
                continue;
            }
            candidates.add(section);
        }
        if (candidates.isEmpty()) {
            return List.of();
        }
        if (summaryConcurrency() <= 1 || candidates.size() == 1) {
            return summarizeSequentially(candidates);
        }

        return summarizeConcurrently(candidates);
    }

    private List<KnowledgeChunkDraft> summarizeSequentially(List<KnowledgeSectionDraft> sections) {
        List<KnowledgeChunkDraft> summaries = new ArrayList<>();
        for (KnowledgeSectionDraft section : sections) {
            KnowledgeChunkDraft summary = summarizeToChunk(section);
            if (summary != null) {
                summaries.add(summary);
            }
        }
        return summaries;
    }

    private List<KnowledgeChunkDraft> summarizeConcurrently(List<KnowledgeSectionDraft> sections) {
        List<CompletableFuture<KnowledgeChunkDraft>> futures = new ArrayList<>(sections.size());
        for (KnowledgeSectionDraft section : sections) {
            futures.add(CompletableFuture.supplyAsync(() -> summarizeToChunk(section), summaryExecutor));
        }

        List<KnowledgeChunkDraft> summaries = new ArrayList<>(sections.size());
        try {
            for (CompletableFuture<KnowledgeChunkDraft> future : futures) {
                KnowledgeChunkDraft summary = joinSummary(future);
                if (summary != null) {
                    summaries.add(summary);
                }
            }
            return summaries;
        } catch (RuntimeException exception) {
            cancelPending(futures);
            throw exception;
        }
    }

    private KnowledgeChunkDraft joinSummary(CompletableFuture<KnowledgeChunkDraft> future) {
        try {
            return future.join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Error error) {
                throw error;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
    }

    private void cancelPending(List<CompletableFuture<KnowledgeChunkDraft>> futures) {
        for (CompletableFuture<KnowledgeChunkDraft> future : futures) {
            future.cancel(true);
        }
    }

    private KnowledgeChunkDraft summarizeToChunk(KnowledgeSectionDraft section) {
        String summary = summarize(section);
        if (summary.isBlank()) {
            return null;
        }
        return KnowledgeChunkDraft.sectionSummary(
                section.getHeadingBlockId(),
                section.getHeadingPath(),
                section.getChunkIndex() == null ? 0 : section.getChunkIndex(),
                summary
        );
    }

    private int summaryConcurrency() {
        return Math.max(1, properties.getSummaryConcurrency());
    }

    private String summarize(KnowledgeSectionDraft section) {
        String modelId = properties.getSummaryModelId();
        int chunkIndex = section.getChunkIndex() == null ? 0 : section.getChunkIndex();
        int inputChars = charCount(section.getContent());
        long start = System.currentTimeMillis();
        log.info("knowledge summary model call start modelId={} headingBlockId={} chunkIndex={} inputChars={} maxInputChars={} maxOutputTokens={}",
                modelId,
                section.getHeadingBlockId(),
                chunkIndex,
                inputChars,
                properties.getSummaryMaxInputChars(),
                properties.getSummaryMaxOutputTokens());
        ChatRequest request = new ChatRequest();
        request.setTemperature(0.1);
        request.setMaxOutputTokens(properties.getSummaryMaxOutputTokens());
        request.setMessages(List.of(
                new ChatMessage("system", summaryPrompts().getSystem()),
                new ChatMessage("user", prompt(section))
        ));
        try {
            ChatResponse response = aiModelRegistry.resolve(modelId).chat(request);
            String summary = firstText(response).strip();
            log.info("knowledge summary model call done modelId={} headingBlockId={} chunkIndex={} inputChars={} summaryChars={} durationMs={}",
                    modelId,
                    section.getHeadingBlockId(),
                    chunkIndex,
                    inputChars,
                    summary.length(),
                    System.currentTimeMillis() - start);
            return summary;
        } catch (RuntimeException exception) {
            log.error("knowledge summary model call failed modelId={} headingBlockId={} chunkIndex={} inputChars={} durationMs={}",
                    modelId,
                    section.getHeadingBlockId(),
                    chunkIndex,
                    inputChars,
                    System.currentTimeMillis() - start,
                    exception);
            throw exception;
        }
    }

    private String prompt(KnowledgeSectionDraft section) {
        String heading = String.join(" / ", section.getHeadingPath());
        String content = truncate(section.getContent(), properties.getSummaryMaxInputChars());
        return applyTemplate(summaryPrompts().getUser(), heading, content);
    }

    private String firstText(ChatResponse response) {
        if (response == null || response.getChoices() == null) {
            return "";
        }
        for (ChatChoice choice : response.getChoices()) {
            if (choice == null || choice.getMessage() == null || choice.getMessage().getContent() == null) {
                continue;
            }
            Object content = choice.getMessage().getContent();
            if (content instanceof String text) {
                return text;
            }
            return String.valueOf(content);
        }
        return "";
    }

    private int charCount(String value) {
        return value == null ? 0 : value.length();
    }

    private KnowledgeProperties.SummaryPromptProperties summaryPrompts() {
        KnowledgeProperties.PromptProperties prompts = properties.getPrompts();
        if (prompts == null || prompts.getSummary() == null) {
            throw new IllegalStateException("Knowledge summary prompts are not configured");
        }
        KnowledgeProperties.SummaryPromptProperties summary = prompts.getSummary();
        if (isBlank(summary.getSystem())) {
            throw new IllegalStateException("Knowledge summary system prompt is not configured");
        }
        if (isBlank(summary.getUser())) {
            throw new IllegalStateException("Knowledge summary user prompt is not configured");
        }
        return summary;
    }

    private String applyTemplate(String template, String headingPath, String content) {
        return template
                .replace("{{headingPath}}", headingPath == null ? "" : headingPath)
                .replace("{{content}}", content == null ? "" : content);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String truncate(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        if (maxChars <= 0 || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars);
    }

}
