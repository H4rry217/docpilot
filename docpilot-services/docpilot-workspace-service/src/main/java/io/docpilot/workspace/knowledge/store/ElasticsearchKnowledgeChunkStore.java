package io.docpilot.workspace.knowledge.store;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.docpilot.ai.AiEmbeddingModel;
import io.docpilot.ai.AiEmbeddingRegistry;
import io.docpilot.ai.model.EmbeddingRequest;
import io.docpilot.ai.model.EmbeddingResponse;
import io.docpilot.workspace.knowledge.KnowledgeRetrievalProvider;
import io.docpilot.workspace.knowledge.config.KnowledgeProperties;
import io.docpilot.workspace.knowledge.model.KnowledgeChunkType;
import io.docpilot.workspace.knowledge.model.KnowledgeIndexedChunk;
import io.docpilot.workspace.knowledge.model.KnowledgeRetrievalRequest;
import io.docpilot.workspace.knowledge.model.KnowledgeRetrievalResult;
import io.docpilot.workspace.knowledge.model.entity.KnowledgeChunkES;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Elasticsearch-backed chunk store.
 */
public class ElasticsearchKnowledgeChunkStore implements KnowledgeChunkStore, KnowledgeRetrievalProvider {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchKnowledgeChunkStore.class);

    /**
     * Elasticsearch client used for chunk indexing and retrieval queries.
     */
    private final ElasticsearchClient client;

    /**
     * Knowledge index configuration and model ids.
     */
    private final KnowledgeProperties properties;

    /**
     * Embedding registry used by the ES provider to vectorize query text.
     */
    private final AiEmbeddingRegistry embeddingRegistry;

    public ElasticsearchKnowledgeChunkStore(ElasticsearchClient client,
                                            KnowledgeProperties properties,
                                            AiEmbeddingRegistry embeddingRegistry) {
        this.client = client;
        this.properties = properties;
        this.embeddingRegistry = embeddingRegistry;
    }

    @Override
    public void replaceDocumentChunks(Long workspaceId, Long documentId, List<KnowledgeIndexedChunk> chunks) {
        int chunkCount = chunks == null ? 0 : chunks.size();
        log.info("knowledge es replace start index={} workspaceId={} documentId={} chunks={}",
                properties.getIndexName(), workspaceId, documentId, chunkCount);
        deleteDocumentChunks(workspaceId, documentId);
        if (chunks == null || chunks.isEmpty()) {
            log.info("knowledge es replace done index={} workspaceId={} documentId={} chunks=0",
                    properties.getIndexName(), workspaceId, documentId);
            return;
        }
        try {
            BulkResponse response = client.bulk(builder -> {
                builder.index(properties.getIndexName());
                for (KnowledgeIndexedChunk chunk : chunks) {
                    KnowledgeChunkES entity = KnowledgeChunkES.fromChunk(chunk);
                    builder.operations(operation -> operation.index(index -> index
                            .id(entity.getId())
                            .document(entity.toSource())));
                }
                return builder;
            });
            if (response.errors()) {
                throw new IllegalStateException("Failed to bulk index knowledge chunks: " + response.items());
            }
            log.info("knowledge es bulk indexed index={} workspaceId={} documentId={} chunks={} items={}",
                    properties.getIndexName(), workspaceId, documentId, chunks.size(), response.items().size());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to bulk index knowledge chunks", exception);
        }
    }

    @Override
    public void deleteDocumentChunks(Long workspaceId, Long documentId) {
        if (workspaceId == null || documentId == null) {
            log.warn("knowledge es delete skipped reason=invalid_scope index={} workspaceId={} documentId={}",
                    properties.getIndexName(), workspaceId, documentId);
            return;
        }
        try {
            log.info("knowledge es delete start index={} workspaceId={} documentId={}",
                    properties.getIndexName(), workspaceId, documentId);
            var response = client.deleteByQuery(builder -> builder
                    .index(properties.getIndexName())
                    .query(query -> query.bool(bool -> bool
                            .filter(filter -> filter.term(term -> term
                                    .field(KnowledgeChunkES.Fields.workspaceId)
                                    .value(FieldValue.of(workspaceId))))
                            .filter(filter -> filter.term(term -> term
                                    .field(KnowledgeChunkES.Fields.documentId)
                                    .value(FieldValue.of(documentId)))))));
            log.info("knowledge es delete done index={} workspaceId={} documentId={} deleted={}",
                    properties.getIndexName(), workspaceId, documentId, response.deleted());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to delete knowledge chunks", exception);
        }
    }

    @Override
    public KnowledgeRetrievalResult retrieve(KnowledgeRetrievalRequest request) {
        if (request == null || request.getWorkspaceId() == null) {
            return new KnowledgeRetrievalResult(List.of());
        }

        KnowledgeSearchQuery query = new KnowledgeSearchQuery();
        query.setWorkspaceId(request.getWorkspaceId());
        query.setOwnerUserId(request.getOwnerUserId());
        query.setScopeDocumentIds(request.getScopeDocumentIds());
        query.setPreferredDocumentId(request.getPreferredDocumentId());
        query.setRevisionId(request.getRevisionId());
        query.setQueryText(request.getQueryText());
        query.setLimit(request.getLimit());
        query.setIncludeChunkTypes(request.getIncludeChunkTypes());
        query.setQueryEmbedding(embedQuery(request.getQueryText()));
        return new KnowledgeRetrievalResult(search(query));
    }

    @Override
    public List<KnowledgeIndexedChunk> search(KnowledgeSearchQuery query) {
        if (query == null || query.getWorkspaceId() == null) {
            return List.of();
        }
        int limit = Math.max(1, query.getLimit());
        try {
            SearchResponse<Map> response = client.search(builder -> {
                        builder.index(properties.getIndexName())
                                .size(limit)
                                .query(q -> q.bool(boolQuery(query)));

                        if (query.getQueryEmbedding() != null && query.getQueryEmbedding().length > 0) {
                            builder.knn(knn -> {
                                knn.field(KnowledgeChunkES.Fields.embedding)
                                        .queryVector(toList(query.getQueryEmbedding()))
                                        .k(limit)
                                        .numCandidates(Math.max(50, limit * 10))
                                        .boost(1.5F);
                                for (Query filter : filterQueries(query)) {
                                    knn.filter(filter);
                                }
                                return knn;
                            });
                        }
                        return builder;
                    },
                    Map.class);
            List<KnowledgeIndexedChunk> chunks = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                if (hit.source() != null) {
                    KnowledgeIndexedChunk chunk = KnowledgeChunkES.fromSource(hit.id(), hit.source()).toChunk();
                    chunk.setScore(hit.score());
                    chunks.add(chunk);
                }
            }
            return chunks;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to search knowledge chunks", exception);
        }
    }

    private BoolQuery boolQuery(KnowledgeSearchQuery query) {
        BoolQuery.Builder bool = new BoolQuery.Builder();
        for (Query filter : filterQueries(query)) {
            bool.filter(filter);
        }
        // Preferred document only changes ranking; scopeDocumentIds in filters below owns hard visibility.
        if (query.getPreferredDocumentId() != null) {
            bool.should(should -> should.term(term -> term
                    .field(KnowledgeChunkES.Fields.documentId)
                    .value(FieldValue.of(query.getPreferredDocumentId()))
                    .boost(2.0F)));
        }
        bool.should(should -> should.term(term -> term
                .field(KnowledgeChunkES.Fields.chunkType)
                .value(FieldValue.of(KnowledgeChunkType.BLOCK.name()))
                .boost(1.2F)));
        if (query.getQueryText() != null && !query.getQueryText().isBlank()) {
            bool.must(must -> must.multiMatch(match -> match
                    .query(query.getQueryText())
                    .fields(
                            KnowledgeChunkES.Fields.content + "^3",
                            KnowledgeChunkES.Fields.title + "^2",
                            KnowledgeChunkES.Fields.headingPath)));
        }
        return bool.build();
    }

    private List<Query> filterQueries(KnowledgeSearchQuery query) {
        List<Query> filters = new ArrayList<>();
        filters.add(termQuery(KnowledgeChunkES.Fields.workspaceId, FieldValue.of(query.getWorkspaceId())));
        filters.add(termQuery(KnowledgeChunkES.Fields.isDeleted, FieldValue.of(false)));
        if (query.getOwnerUserId() != null) {
            filters.add(termQuery(KnowledgeChunkES.Fields.ownerUserId, FieldValue.of(query.getOwnerUserId())));
        }
        if (query.getRevisionId() != null) {
            filters.add(termQuery(KnowledgeChunkES.Fields.revisionId, FieldValue.of(query.getRevisionId())));
        }
        // Scope document ids are hard filters so path-scoped retrieval cannot leak sibling documents.
        if (query.getScopeDocumentIds() != null && !query.getScopeDocumentIds().isEmpty()) {
            filters.add(termsQuery(KnowledgeChunkES.Fields.documentId, query.getScopeDocumentIds().stream()
                    .map(FieldValue::of)
                    .toList()));
        }
        // Chunk type filters are applied to both BM25 and kNN clauses through the shared filter list.
        if (query.getIncludeChunkTypes() != null && !query.getIncludeChunkTypes().isEmpty()) {
            filters.add(termsQuery(KnowledgeChunkES.Fields.chunkType, query.getIncludeChunkTypes().stream()
                    .map(FieldValue::of)
                    .toList()));
        }
        return filters;
    }

    private Query termQuery(String field, FieldValue value) {
        return Query.of(query -> query.term(term -> term
                .field(field)
                .value(value)));
    }

    private Query termsQuery(String field, List<FieldValue> values) {
        return Query.of(query -> query.terms(terms -> terms
                .field(field)
                .terms(termsValue -> termsValue.value(values))));
    }

    private float[] embedQuery(String queryText) {
        if (queryText == null || queryText.isBlank()) {
            return null;
        }
        AiEmbeddingModel embeddingModel = embeddingRegistry.resolve(properties.getEmbeddingModelId());
        EmbeddingRequest request = new EmbeddingRequest();
        request.setInputs(List.of(queryText));
        EmbeddingResponse response = embeddingModel.embed(request);
        if (response.getEmbeddings() == null || response.getEmbeddings().isEmpty()) {
            return null;
        }
        return response.getEmbeddings().getFirst();
    }

    private List<Float> toList(float[] values) {
        if (values == null) {
            return null;
        }
        List<Float> result = new ArrayList<>(values.length);
        for (float value : values) {
            result.add(value);
        }
        return result;
    }

}
