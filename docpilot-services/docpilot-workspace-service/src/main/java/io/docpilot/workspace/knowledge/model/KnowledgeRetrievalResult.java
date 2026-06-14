package io.docpilot.workspace.knowledge.model;

import java.util.List;

/**
 * Retrieved knowledge chunks in ranking order.
 */
public record KnowledgeRetrievalResult(
        /**
         * Ranked chunks returned by the active retrieval provider.
         */
        List<KnowledgeIndexedChunk> chunks
) {
}
