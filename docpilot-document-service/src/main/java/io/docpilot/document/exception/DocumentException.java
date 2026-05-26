package io.docpilot.document.exception;

import io.docpilot.common.exception.DocPilotException;
import io.docpilot.common.result.StatusCode;

/**
 * Base runtime exception for the document module.
 */
public class DocumentException extends DocPilotException {

    public DocumentException(String message) {
        this(StatusCode.BUSINESS_ERROR, message);
    }

    public DocumentException(StatusCode statusCode, String message) {
        super(statusCode, message);
    }

}
