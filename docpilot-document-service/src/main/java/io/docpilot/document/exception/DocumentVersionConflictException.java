package io.docpilot.document.exception;

/**
 * Raised when an optimistic version check fails.
 */
public class DocumentVersionConflictException extends DocumentException {

    public DocumentVersionConflictException(String message) {
        super(message);
    }

}
