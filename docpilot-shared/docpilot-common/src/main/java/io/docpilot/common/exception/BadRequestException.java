package io.docpilot.common.exception;

import io.docpilot.common.result.StatusCode;

public class BadRequestException extends DocPilotException {

    public BadRequestException(String message) {
        super(StatusCode.BAD_REQUEST, message);
    }

}
