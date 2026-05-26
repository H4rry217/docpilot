package io.docpilot.document.exception;

/**
 * Raised when a document id cannot be resolved.
 */
public class DocumentNotFoundException extends DocumentException {

    public DocumentNotFoundException(String message) {
        super(message);
    }

}
