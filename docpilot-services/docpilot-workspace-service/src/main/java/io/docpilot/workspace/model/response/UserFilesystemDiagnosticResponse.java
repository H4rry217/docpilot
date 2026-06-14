package io.docpilot.workspace.model.response;

/**
 * Non-fatal diagnostic returned by best-effort user filesystem operations.
 *
 * @param path user-visible path where the diagnostic occurred.
 * @param code stable diagnostic code.
 * @param message human-readable diagnostic message.
 */
public record UserFilesystemDiagnosticResponse(
        String path,
        String code,
        String message
) {
}
