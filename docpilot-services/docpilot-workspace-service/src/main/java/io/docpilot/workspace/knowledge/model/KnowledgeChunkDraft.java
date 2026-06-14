package io.docpilot.workspace.knowledge.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Chunk before embedding and audit fields are attached.
 */
@Getter
@Setter
public class KnowledgeChunkDraft {

    /**
     * Chunk role in retrieval, such as source block or generated section summary.
     */
    private KnowledgeChunkType chunkType;

    /**
     * Source block id; summary chunks point at the heading block for their section.
     */
    private String blockId;

    /**
     * Source block type for tracing a hit back to the document structure.
     */
    private String blockType;

    /**
     * Stable order of chunks generated from the same block or summary source.
     */
    private Integer chunkIndex = 0;

    /**
     * Heading labels from document root to this chunk's source location.
     */
    private List<String> headingPath = new ArrayList<>();

    /**
     * Text that will be embedded and returned as retrieval context.
     */
    private String content;

    public static KnowledgeChunkDraft block(String blockId,
                                            String blockType,
                                            int chunkIndex,
                                            List<String> headingPath,
                                            String content) {
        KnowledgeChunkDraft draft = new KnowledgeChunkDraft();
        draft.setChunkType(KnowledgeChunkType.BLOCK);
        draft.setBlockId(blockId);
        draft.setBlockType(blockType);
        draft.setChunkIndex(chunkIndex);
        draft.setHeadingPath(headingPath == null ? new ArrayList<>() : new ArrayList<>(headingPath));
        draft.setContent(content);
        return draft;
    }

    public static KnowledgeChunkDraft sectionSummary(String headingBlockId,
                                                     List<String> headingPath,
                                                     String content) {
        return sectionSummary(headingBlockId, headingPath, 0, content);
    }

    public static KnowledgeChunkDraft sectionSummary(String headingBlockId,
                                                     List<String> headingPath,
                                                     int chunkIndex,
                                                     String content) {
        KnowledgeChunkDraft draft = new KnowledgeChunkDraft();
        draft.setChunkType(KnowledgeChunkType.SECTION_SUMMARY);
        draft.setBlockId(headingBlockId);
        draft.setBlockType(io.docpilot.block.model.BlockType.HEADING.name());
        draft.setChunkIndex(chunkIndex);
        draft.setHeadingPath(headingPath == null ? new ArrayList<>() : new ArrayList<>(headingPath));
        draft.setContent(content);
        return draft;
    }

}
