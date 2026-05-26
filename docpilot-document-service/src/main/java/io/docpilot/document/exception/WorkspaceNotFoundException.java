package io.docpilot.document.exception;

import io.docpilot.common.result.StatusCode;

/**
 * Raised when a workspace id cannot be resolved by the storage boundary.
 */
public class WorkspaceNotFoundException extends DocumentException {

    public WorkspaceNotFoundException(String message) {
        super(StatusCode.NOT_FOUND, message);
    }

}
