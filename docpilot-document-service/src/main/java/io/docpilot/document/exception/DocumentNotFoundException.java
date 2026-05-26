package io.docpilot.document.exception;

import io.docpilot.common.result.StatusCode;

/**
 * Raised when a document id cannot be resolved.
 */
public class DocumentNotFoundException extends DocumentException {

    public DocumentNotFoundException(String message) {
        super(StatusCode.NOT_FOUND, message);
    }

}
