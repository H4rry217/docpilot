package io.docpilot.document.model;

import io.docpilot.document.user.UserProfile;
import lombok.Getter;
import lombok.Setter;

/**
 * Read model that combines a document with optional owner information.
 */
@Getter
@Setter
public class DocumentDetail {

    /**
     * Document aggregate.
     */
    private DocPilotDocument document;

    /**
     * Owner profile resolved from the user layer.
     */
    private UserProfile ownerProfile;

}
