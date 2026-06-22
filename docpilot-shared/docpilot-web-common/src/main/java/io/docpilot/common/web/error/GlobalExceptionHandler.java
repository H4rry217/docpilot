package io.docpilot.common.web.error;

import io.docpilot.common.exception.DocPilotException;
import io.docpilot.common.result.Result;
import io.docpilot.common.result.StatusCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Component("docPilotGlobalExceptionHandler")
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DocPilotException.class)
    public ResponseEntity<Result<Void>> handleDocPilotException(DocPilotException exception) {
        StatusCode statusCode = exception.getStatusCode();
        if (statusCode.httpStatus() >= 500) {
            log.error("DocPilot exception: {}", exception.getMessage(), exception);
        } else {
            log.warn("DocPilot exception: {}", exception.getMessage());
        }
        return toResponse(statusCode, exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> handleBadRequest(IllegalArgumentException exception) {
        log.warn("Bad request: {}", exception.getMessage());
        return toResponse(StatusCode.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception exception) {
        log.error("Unhandled exception", exception);
        return toResponse(StatusCode.SYSTEM_ERROR, StatusCode.SYSTEM_ERROR.defaultMsg());
    }

    private ResponseEntity<Result<Void>> toResponse(StatusCode statusCode, String message) {
        return new ResponseEntity<>(
                Result.failure(statusCode, message),
                HttpStatusCode.valueOf(statusCode.httpStatus())
        );
    }

}
