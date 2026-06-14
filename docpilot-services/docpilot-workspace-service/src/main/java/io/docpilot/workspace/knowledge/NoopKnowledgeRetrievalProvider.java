package io.docpilot.workspace.knowledge;

import io.docpilot.workspace.knowledge.model.KnowledgeRetrievalRequest;
import io.docpilot.workspace.knowledge.model.KnowledgeRetrievalResult;

import java.util.List;

/**
 * Disabled retrieval provider used when semantic retrieval is not configured.
 */
public class NoopKnowledgeRetrievalProvider implements KnowledgeRetrievalProvider {

    @Override
    public KnowledgeRetrievalResult retrieve(KnowledgeRetrievalRequest request) {
        return new KnowledgeRetrievalResult(List.of());
    }

}
