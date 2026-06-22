package io.docpilot.workspace.config;

import io.docpilot.ai.AiModelRegistry;
import io.docpilot.workspace.infrastructure.mongo.MongoWorkspaceTransactionRunner;
import io.docpilot.workspace.application.WorkspaceTransactionRunner;
import io.docpilot.workspace.inlinecompletion.InlineCompletionProperties;
import io.docpilot.workspace.knowledge.AiKnowledgeSummaryService;
import io.docpilot.workspace.knowledge.KnowledgeChunker;
import io.docpilot.workspace.knowledge.KnowledgeIndexCommandHandler;
import io.docpilot.workspace.knowledge.KnowledgeRetrievalProvider;
import io.docpilot.workspace.knowledge.KnowledgeSummaryService;
import io.docpilot.workspace.knowledge.NoopKnowledgeRetrievalProvider;
import io.docpilot.workspace.knowledge.config.KnowledgeProperties;
import io.docpilot.workspace.knowledge.queue.DirectKnowledgeIndexQueue;
import io.docpilot.workspace.knowledge.queue.KnowledgeIndexJobLock;
import io.docpilot.workspace.knowledge.queue.KnowledgeIndexQueue;
import io.docpilot.workspace.knowledge.queue.RedisKnowledgeIndexJobStore;
import io.docpilot.workspace.knowledge.queue.RedisKnowledgeIndexQueue;
import io.docpilot.workspace.knowledge.store.KnowledgeChunkStore;
import io.docpilot.workspace.knowledge.store.NoopKnowledgeChunkStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Spring configuration for workspace application services and knowledge indexing ports.
 */
@Configuration
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties({KnowledgeProperties.class, InlineCompletionProperties.class})
public class WorkspaceApplicationConfig {

    private static final AtomicInteger KNOWLEDGE_SUMMARY_THREAD_SEQUENCE = new AtomicInteger();

    @Bean
    @ConditionalOnMissingBean
    public WorkspaceTransactionRunner workspaceTransactionRunner(MongoDatabaseFactory mongoDatabaseFactory) {
        MongoWorkspaceTransactionRunner transactionRunner = new MongoWorkspaceTransactionRunner();
        transactionRunner.setMongoDatabaseFactory(mongoDatabaseFactory);
        return transactionRunner;
    }

    @Bean
    @ConditionalOnMissingBean
    public KnowledgeChunker knowledgeChunker(KnowledgeProperties properties) {
        return new KnowledgeChunker(properties.getSummaryMaxInputChars());
    }

    @Bean
    @ConditionalOnMissingBean
    public KnowledgeSummaryService knowledgeSummaryService(AiModelRegistry aiModelRegistry,
                                                           KnowledgeProperties properties,
                                                           @Qualifier("knowledgeSummaryExecutor") Executor summaryExecutor) {
        return new AiKnowledgeSummaryService(aiModelRegistry, properties, summaryExecutor);
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "knowledgeSummaryExecutor")
    public ExecutorService knowledgeSummaryExecutor(KnowledgeProperties properties) {
        int concurrency = Math.max(1, properties.getSummaryConcurrency());
        return Executors.newFixedThreadPool(concurrency, runnable -> {
            Thread thread = new Thread(runnable,
                    "knowledge-summary-" + KNOWLEDGE_SUMMARY_THREAD_SEQUENCE.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean
    @ConditionalOnMissingBean
    public KnowledgeChunkStore knowledgeChunkStore() {
        return new NoopKnowledgeChunkStore();
    }

    /**
     * Provides a disabled retrieval provider so retrieval wiring does not require Elasticsearch.
     */
    @Bean
    @ConditionalOnMissingBean
    public KnowledgeRetrievalProvider knowledgeRetrievalProvider() {
        return new NoopKnowledgeRetrievalProvider();
    }

    @Bean
    @ConditionalOnMissingBean(KnowledgeIndexQueue.class)
    @ConditionalOnProperty(prefix = "docpilot.knowledge.index", name = "mode", havingValue = "direct")
    public KnowledgeIndexQueue directKnowledgeIndexQueue(KnowledgeProperties properties,
                                                          KnowledgeIndexCommandHandler handler) {
        return new DirectKnowledgeIndexQueue(properties, handler);
    }

    @Bean
    @ConditionalOnMissingBean(KnowledgeIndexQueue.class)
    @ConditionalOnProperty(prefix = "docpilot.knowledge.index", name = "mode", havingValue = "redis", matchIfMissing = true)
    public KnowledgeIndexQueue redisKnowledgeIndexQueue(KnowledgeProperties properties,
                                                         StringRedisTemplate redisTemplate,
                                                         KnowledgeIndexJobLock jobLock,
                                                         KnowledgeIndexCommandHandler handler) {
        return new RedisKnowledgeIndexQueue(
                properties,
                new RedisKnowledgeIndexJobStore(redisTemplate),
                jobLock,
                handler,
                Clock.systemUTC()
        );
    }

}
