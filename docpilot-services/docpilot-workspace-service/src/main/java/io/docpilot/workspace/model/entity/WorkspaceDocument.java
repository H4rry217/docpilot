package io.docpilot.workspace.model.entity;

import io.docpilot.block.model.BlockDocument;
import io.docpilot.common.domain.BaseEntity;
import io.docpilot.workspace.constant.WorkspaceMongoConstant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Document(collection = WorkspaceMongoConstant.DOCUMENT_COLLECTION)
public class WorkspaceDocument extends BaseEntity {

    /**
     * Owner user id.
     */
    private Long ownerUserId;

    /**
     * Workspace where the document was created.
     */
    private Long originWorkspaceId;

    /**
     * Document title.
     */
    private String title;

    /**
     * Current document version.
     */
    private Long currentVersion;

    /**
     * Current revision id.
     */
    private Long currentRevisionId;

    /**
     * User-defined document metadata.
     */
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * Current document content snapshot.
     */
    private DocumentContent content;

    @Getter
    @Setter
    public static class DocumentContent {

        /**
         * Block schema version used by the content payload.
         */
        private String blockSchemaVersion;

        /**
         * Structured block document.
         */
        private BlockDocument blockDocument;

        /**
         * Markdown representation of the current content.
         */
        private String markdownText;

        /**
         * Content checksum.
         */
        private String checksum;

    }

}
