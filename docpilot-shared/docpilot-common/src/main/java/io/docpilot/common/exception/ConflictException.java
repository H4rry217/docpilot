package io.docpilot.common.exception;

import io.docpilot.common.result.StatusCode;

public class ConflictException extends DocPilotException {

    public ConflictException(String message) {
        super(StatusCode.CONFLICT, message);
    }

}
