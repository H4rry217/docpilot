package io.docpilot.filesystem.exception;

public class ReadonlyPathException extends FilesystemException {

    public ReadonlyPathException(String message) {
        super(message);
    }

}
