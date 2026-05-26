package io.docpilot.document.exception;

/**
 * Raised when the current subject cannot perform a document action.
 */
public class DocumentAccessDeniedException extends DocumentException {

    public DocumentAccessDeniedException(String message) {
        super(message);
    }

}
