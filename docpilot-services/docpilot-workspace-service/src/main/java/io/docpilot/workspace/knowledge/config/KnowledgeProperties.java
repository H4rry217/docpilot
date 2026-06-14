package io.docpilot.workspace.knowledge.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Knowledge index configuration.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "docpilot.knowledge")
public class KnowledgeProperties {

    /**
     * Default ES index name for knowledge chunks.
     */
    public static final String DEFAULT_INDEX_NAME = "docpilot_knowledge_chunk_search";

    /**
     * Global switch for built-in knowledge indexing and retrieval.
     */
    private boolean enabled = false;

    /**
     * Elasticsearch index that stores DocPilot-managed knowledge chunks.
     */
    private String indexName = DEFAULT_INDEX_NAME;

    /**
     * AI embedding model id used by the built-in indexing and retrieval provider.
     */
    private String embeddingModelId;

    /**
     * AI chat model id used to generate section summary chunks.
     */
    private String summaryModelId;

    /**
     * Maximum characters from one section sent to the summary model.
     */
    private int summaryMaxInputChars = 1000;

    /**
     * Maximum output tokens requested when generating one section summary chunk.
     */
    private int summaryMaxOutputTokens = 256;

    /**
     * Maximum concurrent summary model calls in one service instance.
     */
    private int summaryConcurrency = 3;

    /**
     * Number of chunks embedded in one provider request.
     */
    private int embeddingBatchSize = 10;

    /**
     * Debounced indexing queue settings.
     */
    private IndexProperties index = new IndexProperties();

    /**
     * Prompt templates used by knowledge model calls.
     */
    private PromptProperties prompts = new PromptProperties();

    @Getter
    @Setter
    public static class IndexProperties {

        /**
         * Queue mode: redis for debounced distributed indexing, direct for immediate local indexing.
         */
        private String mode = "redis";

        /**
         * Time to wait after the latest save before indexing.
         */
        private long debounceDelayMs = 3000;

        /**
         * Maximum time a document can be delayed since the first queued save.
         */
        private long maxDelayMs = 30000;

        /**
         * Worker polling interval for due Redis jobs.
         */
        private long pollIntervalMs = 1000;

        /**
         * Maximum due documents one worker tick processes.
         */
        private int workerBatchSize = 20;

        /**
         * Redis lock TTL while one document is being indexed.
         */
        private long lockTtlMs = 1800000;

        /**
         * Delay before retrying a failed indexing job.
         */
        private long retryDelayMs = 10000;

        /**
         * TTL for queued job metadata.
         */
        private long jobTtlMs = 86400000;

    }

    @Getter
    @Setter
    public static class PromptProperties {

        /**
         * Prompt templates for section summary generation.
         */
        private SummaryPromptProperties summary = new SummaryPromptProperties();

    }

    @Getter
    @Setter
    public static class SummaryPromptProperties {

        /**
         * System prompt sent to the summary model.
         */
        private String system;

        /**
         * User prompt template. Supports {{headingPath}} and {{content}}.
         */
        private String user;

    }

}
