package io.docpilot.document.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Mutable command for sharing a document with another user.
 */
@Getter
@Setter
public class ShareDocumentCommand {

    /**
     * Document id to share.
     */
    private String documentId;

    /**
     * Target user id.
     */
    private String targetUserId;

    /**
     * Granted document role.
     */
    private DocumentRole role = DocumentRole.VIEWER;

}
