package io.docpilot.workspace.model.entity;

import io.docpilot.block.model.BlockDocument;
import io.docpilot.common.domain.BaseEntity;
import io.docpilot.workspace.constant.WorkspaceMongoConstant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Document(collection = WorkspaceMongoConstant.DOCUMENT_REVISION_COLLECTION)
public class DocumentRevision extends BaseEntity {

    /**
     * Document id this revision belongs to.
     */
    private Long documentId;

    /**
     * Revision version number.
     */
    private Long version;

    /**
     * Version used as the save base.
     */
    private Long baseVersion;

    /**
     * User id that created this revision.
     */
    private Long authorUserId;

    /**
     * Full block snapshot for v1.
     */
    private BlockDocument snapshot;

    /**
     * Markdown snapshot rendered from the block document.
     */
    private String markdownSnapshot;

    /**
     * Snapshot checksum.
     */
    private String checksum;

}
