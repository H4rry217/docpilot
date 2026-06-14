package io.docpilot.workspace.knowledge.model;

/**
 * Search chunk source type stored as a keyword in Elasticsearch.
 */
public enum KnowledgeChunkType {

    /**
     * Chunk rendered from an original BlockDocument block.
     */
    BLOCK,

    /**
     * LLM-generated summary for one heading section.
     */
    SECTION_SUMMARY

}
