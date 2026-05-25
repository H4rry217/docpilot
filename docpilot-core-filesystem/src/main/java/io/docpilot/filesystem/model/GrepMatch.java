package io.docpilot.filesystem.model;

public record GrepMatch(
        String path,
        long lineNumber,
        String line
) {
}
