package io.docpilot.ai;

import io.docpilot.ai.model.EmbeddingRequest;
import io.docpilot.ai.model.EmbeddingResponse;

/**
 * Provider-neutral embedding model boundary used by indexing and retrieval services.
 */
public interface AiEmbeddingModel {

    /**
     * Stable registry id for this configured embedding model.
     */
    String id();

    /**
     * Metadata describing configured model identity and known capability limits.
     */
    AiModelMetadata metadata();

    /**
     * Creates embeddings for the supplied input texts.
     */
    EmbeddingResponse embed(EmbeddingRequest request);

}
