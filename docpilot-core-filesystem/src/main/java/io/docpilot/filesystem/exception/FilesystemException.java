package io.docpilot.filesystem.exception;

/**
 * Base exception for virtual filesystem operations.
 */
public class FilesystemException extends RuntimeException {

    public FilesystemException(String message) {
        super(message);
    }

    public FilesystemException(String message, Throwable cause) {
        super(message, cause);
    }

}
