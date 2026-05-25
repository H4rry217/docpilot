package io.docpilot.document.exception;

/**
 * Base runtime exception for the document module.
 */
public class DocumentException extends RuntimeException {

    public DocumentException(String message) {
        super(message);
    }

}
