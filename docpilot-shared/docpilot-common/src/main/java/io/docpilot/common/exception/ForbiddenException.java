package io.docpilot.common.exception;

import io.docpilot.common.result.StatusCode;

public class ForbiddenException extends DocPilotException {

    public ForbiddenException(String message) {
        super(StatusCode.FORBIDDEN, message);
    }

}
