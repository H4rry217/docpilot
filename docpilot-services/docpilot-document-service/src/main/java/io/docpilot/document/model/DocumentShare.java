package io.docpilot.document.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * File-level share from a document owner to a target user.
 */
@Getter
@Setter
public class DocumentShare {

    /**
     * Share id used by APIs and storage.
     */
    private String shareId;

    /**
     * Shared document id.
     */
    private String documentId;

    /**
     * User id of the document owner.
     */
    private Long ownerUserId;

    /**
     * User id receiving the share.
     */
    private Long targetUserId;

    /**
     * Role granted to the target user.
     */
    private DocumentRole role = DocumentRole.VIEWER;

    /**
     * Share lifecycle state.
     */
    private DocumentShareState state = DocumentShareState.ACTIVE;

    /**
     * Creation time.
     */
    private Instant createTime;

    /**
     * Last update time.
     */
    private Instant updateTime;

    /**
     * Extension metadata for future sharing features.
     */
    private Map<String, Object> metadata = new HashMap<>();

}
