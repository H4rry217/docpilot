package io.docpilot.workspace.config;

import io.docpilot.block.processing.MarkdownBlockParser;
import io.docpilot.block.processing.MarkdownBlockRenderer;
import io.docpilot.ai.AiEmbeddingRegistry;
import io.docpilot.ai.AiModelRegistry;
import io.docpilot.common.auth.AuthContextProvider;
import io.docpilot.common.id.SnowflakeIdGenerator;
import io.docpilot.workspace.infrastructure.mongo.MongoWorkspaceTransactionRunner;
import io.docpilot.workspace.application.DocumentApplicationService;
import io.docpilot.workspace.application.WorkspaceApplicationService;
import io.docpilot.workspace.application.WorkspaceTransactionRunner;
import io.docpilot.workspace.knowledge.AiKnowledgeSummaryService;
import io.docpilot.workspace.knowledge.KnowledgeChunker;
import io.docpilot.workspace.knowledge.KnowledgeIndexCommandHandler;
import io.docpilot.workspace.knowledge.KnowledgeRetrievalProvider;
import io.docpilot.workspace.knowledge.KnowledgeRetrievalService;
import io.docpilot.workspace.knowledge.KnowledgeSummaryService;
import io.docpilot.workspace.knowledge.NoopKnowledgeRetrievalProvider;
import io.docpilot.workspace.knowledge.config.KnowledgeProperties;
import io.docpilot.workspace.knowledge.event.KnowledgeIndexEventListener;
import io.docpilot.workspace.knowledge.queue.DirectKnowledgeIndexQueue;
import io.docpilot.workspace.knowledge.queue.KnowledgeIndexJobLock;
import io.docpilot.workspace.knowledge.queue.KnowledgeIndexQueue;
import io.docpilot.workspace.knowledge.queue.RedissonKnowledgeIndexJobLock;
import io.docpilot.workspace.knowledge.queue.RedisKnowledgeIndexJobStore;
import io.docpilot.workspace.knowledge.queue.RedisKnowledgeIndexQueue;
import io.docpilot.workspace.knowledge.store.KnowledgeChunkStore;
import io.docpilot.workspace.knowledge.store.NoopKnowledgeChunkStore;
import io.docpilot.workspace.processing.WorkspaceIdCodec;
import io.docpilot.workspace.processing.WorkspaceNodeName;
import io.docpilot.workspace.repository.DocumentRevisionRepository;
import io.docpilot.workspace.repository.WorkspaceDocumentRepository;
import io.docpilot.workspace.repository.WorkspaceNodeRepository;
import io.docpilot.workspace.repository.WorkspaceRepository;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.StringUtils;

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
@EnableConfigurationProperties({KnowledgeProperties.class, RedisProperties.class})
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
    public WorkspaceApplicationService workspaceApplicationService(WorkspaceRepository workspaceRepository,
                                                                   WorkspaceNodeRepository workspaceNodeRepository,
                                                                   WorkspaceDocumentRepository documentRepository,
                                                                   AuthContextProvider authContextProvider,
                                                                   SnowflakeIdGenerator idGenerator,
                                                                   WorkspaceIdCodec idCodec,
                                                                   WorkspaceNodeName workspaceNodeName,
                                                                   WorkspaceTransactionRunner transactionRunner,
                                                                   ApplicationEventPublisher eventPublisher) {
        WorkspaceApplicationService service = new WorkspaceApplicationService();
        service.setWorkspaceRepository(workspaceRepository);
        service.setNodeRepository(workspaceNodeRepository);
        service.setDocumentRepository(documentRepository);
        service.setAuthContextProvider(authContextProvider);
        service.setIdGenerator(idGenerator);
        service.setIdCodec(idCodec);
        service.setWorkspaceNodeName(workspaceNodeName);
        service.setTransactionRunner(transactionRunner);
        service.setEventPublisher(eventPublisher);
        service.setClock(Clock.systemDefaultZone());
        return service;
    }

    @Bean
    public DocumentApplicationService documentApplicationService(WorkspaceApplicationService workspaceApplicationService,
                                                                 WorkspaceNodeRepository workspaceNodeRepository,
                                                                 WorkspaceDocumentRepository documentRepository,
                                                                 DocumentRevisionRepository revisionRepository,
                                                                 AuthContextProvider authContextProvider,
                                                                 SnowflakeIdGenerator idGenerator,
                                                                 WorkspaceIdCodec idCodec,
                                                                 WorkspaceNodeName workspaceNodeName,
                                                                 WorkspaceTransactionRunner transactionRunner,
                                                                 ApplicationEventPublisher eventPublisher) {
        DocumentApplicationService service = new DocumentApplicationService();
        service.setWorkspaceService(workspaceApplicationService);
        service.setNodeRepository(workspaceNodeRepository);
        service.setDocumentRepository(documentRepository);
        service.setRevisionRepository(revisionRepository);
        service.setAuthContextProvider(authContextProvider);
        service.setIdGenerator(idGenerator);
        service.setIdCodec(idCodec);
        service.setWorkspaceNodeName(workspaceNodeName);
        service.setTransactionRunner(transactionRunner);
        service.setEventPublisher(eventPublisher);
        service.setMarkdownBlockParser(new MarkdownBlockParser());
        service.setMarkdownBlockRenderer(new MarkdownBlockRenderer());
        return service;
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
    public KnowledgeIndexCommandHandler knowledgeIndexCommandHandler(KnowledgeProperties properties,
                                                                     WorkspaceDocumentRepository documentRepository,
                                                                     DocumentRevisionRepository revisionRepository,
                                                                     KnowledgeChunker chunker,
                                                                     KnowledgeSummaryService summaryService,
                                                                     AiEmbeddingRegistry embeddingRegistry,
                                                                     KnowledgeChunkStore chunkStore) {
        return new KnowledgeIndexCommandHandler(
                properties,
                documentRepository,
                revisionRepository,
                chunker,
                summaryService,
                embeddingRegistry,
                chunkStore
        );
    }

    @Bean
    @ConditionalOnMissingBean(KnowledgeIndexQueue.class)
    @ConditionalOnProperty(prefix = "docpilot.knowledge.index", name = "mode", havingValue = "direct")
    public KnowledgeIndexQueue directKnowledgeIndexQueue(KnowledgeProperties properties,
                                                          KnowledgeIndexCommandHandler handler) {
        return new DirectKnowledgeIndexQueue(properties, handler);
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "docpilot.knowledge.index", name = "mode", havingValue = "redis", matchIfMissing = true)
    public RedissonClient redissonClient(RedisProperties redisProperties) {
        Config config = new Config();
        SingleServerConfig server = config.useSingleServer()
                .setAddress(redisAddress(redisProperties))
                .setDatabase(redisProperties.getDatabase());
        if (StringUtils.hasText(redisProperties.getUsername())) {
            server.setUsername(redisProperties.getUsername());
        }
        if (StringUtils.hasText(redisProperties.getPassword())) {
            server.setPassword(redisProperties.getPassword());
        }
        return Redisson.create(config);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "docpilot.knowledge.index", name = "mode", havingValue = "redis", matchIfMissing = true)
    public KnowledgeIndexJobLock knowledgeIndexJobLock(RedissonClient redissonClient) {
        return new RedissonKnowledgeIndexJobLock(redissonClient);
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

    @Bean
    public KnowledgeIndexEventListener knowledgeIndexEventListener(KnowledgeIndexQueue queue,
                                                                   KnowledgeIndexCommandHandler handler) {
        return new KnowledgeIndexEventListener(queue, handler);
    }

    @Bean
    public KnowledgeRetrievalService knowledgeRetrievalService(KnowledgeProperties properties,
                                                               KnowledgeRetrievalProvider retrievalProvider) {
        return new KnowledgeRetrievalService(properties, retrievalProvider);
    }

    private String redisAddress(RedisProperties redisProperties) {
        return "redis://" + redisProperties.getHost() + ":" + redisProperties.getPort();
    }

}
