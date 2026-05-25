package io.docpilot.controller;

import io.docpilot.controller.dto.ErrorResponse;
import io.docpilot.document.exception.DocumentAccessDeniedException;
import io.docpilot.document.exception.DocumentException;
import io.docpilot.document.exception.DocumentNotFoundException;
import io.docpilot.document.exception.DocumentVersionConflictException;
import io.docpilot.document.exception.WorkspaceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RestExceptionHandler {

    @ExceptionHandler(DocumentVersionConflictException.class)
    public ResponseEntity<ErrorResponse> handleVersionConflict(DocumentVersionConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("DOCUMENT_VERSION_CONFLICT", exception.getMessage()));
    }

    @ExceptionHandler({DocumentNotFoundException.class, WorkspaceNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(DocumentException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(DocumentAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(DocumentAccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("ACCESS_DENIED", exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("BAD_REQUEST", exception.getMessage()));
    }

}
