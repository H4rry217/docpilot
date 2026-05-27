package io.docpilot.document.exception;

import io.docpilot.common.result.StatusCode;

/**
 * Raised when the current subject cannot perform a document action.
 */
public class DocumentAccessDeniedException extends DocumentException {

    public DocumentAccessDeniedException(String message) {
        super(StatusCode.FORBIDDEN, message);
    }

}
