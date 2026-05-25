package io.docpilot.document.exception;

/**
 * Raised when a workspace id cannot be resolved by the storage boundary.
 */
public class WorkspaceNotFoundException extends DocumentException {

    public WorkspaceNotFoundException(String message) {
        super(message);
    }

}
