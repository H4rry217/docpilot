package io.docpilot.common.exception;

import io.docpilot.common.result.StatusCode;

public class UnauthorizedException extends DocPilotException {

    public UnauthorizedException(String message) {
        super(StatusCode.UNAUTHORIZED, message);
    }

}
