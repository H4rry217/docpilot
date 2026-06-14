package io.docpilot.workspace.knowledge;

import io.docpilot.workspace.knowledge.model.KnowledgeRetrievalRequest;
import io.docpilot.workspace.knowledge.model.KnowledgeRetrievalResult;

/**
 * Pluggable knowledge retrieval provider.
 */
public interface KnowledgeRetrievalProvider {

    /**
     * Retrieves ranked knowledge hits for a workspace-scoped request.
     *
     * @param request workspace-aware retrieval request.
     * @return retrieval hits in ranking order.
     */
    KnowledgeRetrievalResult retrieve(KnowledgeRetrievalRequest request);

}
