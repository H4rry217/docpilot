package io.docpilot.document.model;

import io.docpilot.block.model.BlockDocument;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Document aggregate owned by a user.
 */
@Getter
@Setter
public class DocPilotDocument {

    /**
     * Document id used by APIs and storage.
     */
    private String documentId;

    /**
     * Owner user id.
     */
    private Long ownerUserId;

    /**
     * User-facing document title.
     */
    private String title;

    /**
     * Current Markdown source.
     */
    private String markdown = "";

    /**
     * Parsed block representation of the current Markdown source.
     */
    private BlockDocument blockDocument;

    /**
     * Document visibility policy.
     */
    private DocumentVisibility visibility = DocumentVisibility.PRIVATE;

    /**
     * Document lifecycle state.
     */
    private DocumentState state = DocumentState.ACTIVE;

    /**
     * Optimistic version number for later collaboration and conflict checks.
     */
    private long version;

    /**
     * Creation time.
     */
    private Instant createTime;

    /**
     * Last update time.
     */
    private Instant updateTime;

    /**
     * Extension metadata for future document features.
     */
    private Map<String, Object> metadata = new HashMap<>();

}
