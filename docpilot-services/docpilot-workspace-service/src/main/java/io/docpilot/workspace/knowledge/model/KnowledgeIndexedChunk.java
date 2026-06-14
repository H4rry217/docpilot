package io.docpilot.workspace.knowledge.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Provider-neutral knowledge chunk used by indexing and retrieval ports.
 */
@Getter
@Setter
public class KnowledgeIndexedChunk {

    /**
     * Search document id, built from workspace/document/chunk/block identifiers.
     */
    private String id;

    /**
     * Workspace id used as the primary tenant filter.
     */
    private Long workspaceId;

    /**
     * Owner user id used to constrain retrieval to the workspace owner.
     */
    private Long ownerUserId;

    /**
     * Workspace document id that produced this chunk.
     */
    private Long documentId;

    /**
     * Document revision id whose snapshot produced this chunk.
     */
    private Long revisionId;

    /**
     * Current document title, used as auxiliary search text.
     */
    private String title;

    /**
     * Chunk category stored as keyword, such as BLOCK or SECTION_SUMMARY.
     */
    private String chunkType;

    /**
     * Source block id in the revision snapshot.
     */
    private String blockId;

    /**
     * Source block type stored as BlockType.name().
     */
    private String blockType;

    /**
     * Stable index for chunks generated from the same source block.
     */
    private Integer chunkIndex;

    /**
     * Heading labels from document root to the chunk's current section.
     */
    private List<String> headingPath = new ArrayList<>();

    /**
     * Rendered chunk text used for lexical retrieval and embedding.
     */
    private String content;

    /**
     * Provider-specific retrieval score, not persisted as source data.
     */
    private Double score;

    /**
     * Dense vector generated from content for vector retrieval.
     */
    private List<Float> embedding;

    /**
     * BaseEntity-style creation time.
     */
    private LocalDateTime createTime;

    /**
     * BaseEntity-style creator display name.
     */
    private String createBy;

    /**
     * BaseEntity-style creator user id.
     */
    private Long creatorId;

    /**
     * BaseEntity-style update time.
     */
    private LocalDateTime updateTime;

    /**
     * BaseEntity-style updater display name.
     */
    private String updateBy;

    /**
     * BaseEntity-style updater user id.
     */
    private Long updaterId;

    /**
     * BaseEntity-style soft-delete marker.
     */
    private Boolean isDeleted = false;

}
