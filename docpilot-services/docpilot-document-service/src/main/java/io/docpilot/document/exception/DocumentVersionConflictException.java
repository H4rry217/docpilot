package io.docpilot.document.exception;

import io.docpilot.common.result.StatusCode;

/**
 * Raised when an optimistic version check fails.
 */
public class DocumentVersionConflictException extends DocumentException {

    public DocumentVersionConflictException(String message) {
        super(StatusCode.CONFLICT, message);
    }

}
