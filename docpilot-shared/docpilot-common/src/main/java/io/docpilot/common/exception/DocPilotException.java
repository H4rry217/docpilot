package io.docpilot.common.exception;

import io.docpilot.common.result.StatusCode;
import lombok.Getter;

@Getter
public class DocPilotException extends RuntimeException {

    private final StatusCode statusCode;

    public DocPilotException(StatusCode statusCode, String message) {
        super(message == null ? statusCode.defaultMsg() : message);
        this.statusCode = statusCode;
    }

    public DocPilotException(StatusCode statusCode, String message, Throwable cause) {
        super(message == null ? statusCode.defaultMsg() : message, cause);
        this.statusCode = statusCode;
    }

}
