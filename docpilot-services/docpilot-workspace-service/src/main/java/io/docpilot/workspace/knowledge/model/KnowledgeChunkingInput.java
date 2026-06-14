package io.docpilot.workspace.knowledge.model;

import io.docpilot.block.model.BlockDocument;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Document snapshot context supplied to the chunker.
 */
@Getter
@Setter
public class KnowledgeChunkingInput {

    /**
     * Workspace that owns the document snapshot being chunked.
     */
    private Long workspaceId;

    /**
     * User id that owns the workspace and is used for index isolation.
     */
    private Long ownerUserId;

    /**
     * Document aggregate id represented by the snapshot.
     */
    private Long documentId;

    /**
     * Document revision id that produced this snapshot.
     */
    private Long revisionId;

    /**
     * Human-readable document title stored with every chunk for display.
     */
    private String title;

    /**
     * Parsed block snapshot used as the canonical chunking source.
     */
    private BlockDocument snapshot;

    /**
     * Document creation time copied to generated indexed chunks.
     */
    private LocalDateTime createTime;

    /**
     * Creator display or account identifier copied to generated indexed chunks.
     */
    private String createBy;

    /**
     * Creator user id copied to generated indexed chunks.
     */
    private Long creatorId;

    /**
     * Last update time copied to generated indexed chunks.
     */
    private LocalDateTime updateTime;

    /**
     * Last updater display or account identifier copied to generated indexed chunks.
     */
    private String updateBy;

    /**
     * Last updater user id copied to generated indexed chunks.
     */
    private Long updaterId;

}
