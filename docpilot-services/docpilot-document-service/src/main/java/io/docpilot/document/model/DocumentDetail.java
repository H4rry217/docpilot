package io.docpilot.document.model;

import io.docpilot.user.model.UserInformation;
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
     * Owner resolved from the user layer.
     */
    private UserInformation ownerInformation;

}
