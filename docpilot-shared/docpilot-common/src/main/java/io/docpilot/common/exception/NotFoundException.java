package io.docpilot.common.exception;

import io.docpilot.common.result.StatusCode;

public class NotFoundException extends DocPilotException {

    public NotFoundException(String message) {
        super(StatusCode.NOT_FOUND, message);
    }

}
