package io.docpilot.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.docpilot.ai.AiEmbeddingRegistry;
import io.docpilot.workspace.knowledge.config.KnowledgeProperties;
import io.docpilot.workspace.knowledge.store.ElasticsearchKnowledgeChunkStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spring wiring for the built-in Elasticsearch knowledge retrieval provider.
 */
@Configuration
@ConditionalOnProperty(prefix = "docpilot.knowledge", name = "enabled", havingValue = "true")
public class KnowledgeElasticsearchConfig {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeElasticsearchConfig.class);

    /**
     * Creates the built-in Elasticsearch store/provider when the knowledge store type selects it.
     *
     * @param elasticsearchClient low-level ES client used for index reads and writes.
     * @param properties knowledge index and model configuration.
     * @param embeddingRegistry embedding model registry used for query vectors.
     * @return primary DocPilot-managed Elasticsearch knowledge store and retrieval provider.
     */
    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "docpilot.knowledge.store", name = "type", havingValue = "elasticsearch", matchIfMissing = true)
    public ElasticsearchKnowledgeChunkStore elasticsearchKnowledgeChunkStore(ElasticsearchClient elasticsearchClient,
                                                                            KnowledgeProperties properties,
                                                                            AiEmbeddingRegistry embeddingRegistry) {
        log.info("knowledge elasticsearch store enabled indexName={} embeddingModelId={}",
                properties.getIndexName(), properties.getEmbeddingModelId());
        return new ElasticsearchKnowledgeChunkStore(elasticsearchClient, properties, embeddingRegistry);
    }

}
