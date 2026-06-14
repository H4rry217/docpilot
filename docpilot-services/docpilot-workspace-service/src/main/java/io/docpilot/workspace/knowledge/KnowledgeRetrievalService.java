package io.docpilot.workspace.knowledge;

import io.docpilot.workspace.knowledge.config.KnowledgeProperties;
import io.docpilot.workspace.knowledge.model.KnowledgeRetrievalRequest;
import io.docpilot.workspace.knowledge.model.KnowledgeRetrievalResult;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Retrieves knowledge chunks for inline completion context building.
 */
@Service
public class KnowledgeRetrievalService {

    /**
     * Feature flag and knowledge retrieval defaults.
     */
    private final KnowledgeProperties properties;

    /**
     * Pluggable retrieval provider selected by application configuration.
     */
    private final KnowledgeRetrievalProvider retrievalProvider;

    public KnowledgeRetrievalService(KnowledgeProperties properties,
                                     KnowledgeRetrievalProvider retrievalProvider) {
        this.properties = properties;
        this.retrievalProvider = retrievalProvider;
    }

    public KnowledgeRetrievalResult retrieve(KnowledgeRetrievalRequest request) {
        if (!properties.isEnabled() || request == null || request.getQueryText() == null || request.getQueryText().isBlank()) {
            return new KnowledgeRetrievalResult(List.of());
        }

        return retrievalProvider.retrieve(request);
    }

}
