package io.docpilot.workspace.knowledge.model;

import java.util.List;

/**
 * Chunking output containing original block chunks and sections to summarize.
 */
public record KnowledgeChunkingResult(
        /**
         * Source-grounded chunks generated directly from document blocks.
         */
        List<KnowledgeChunkDraft> chunks,

        /**
         * Complex sections that should be summarized before indexing.
         */
        List<KnowledgeSectionDraft> sections
) {
}
