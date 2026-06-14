package io.docpilot.workspace.knowledge;

import io.docpilot.workspace.knowledge.model.KnowledgeChunkDraft;
import io.docpilot.workspace.knowledge.model.KnowledgeSectionDraft;

import java.util.List;

/**
 * Generates section summary chunks.
 */
public interface KnowledgeSummaryService {

    /**
     * Summarizes complex document sections into additional retrieval chunks.
     *
     * @param sections section drafts selected by the chunker.
     * @return summary chunks ready for embedding.
     */
    List<KnowledgeChunkDraft> summarize(List<KnowledgeSectionDraft> sections);

}
