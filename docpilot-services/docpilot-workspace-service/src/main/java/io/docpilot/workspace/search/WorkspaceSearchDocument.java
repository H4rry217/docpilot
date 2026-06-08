package io.docpilot.workspace.search;

/**
 * Immutable search input materialized from a workspace document snapshot.
 *
 * @param path virtual workspace path shown in grep results.
 * @param markdown current Markdown snapshot used as searchable text.
 */
public record WorkspaceSearchDocument(String path, String markdown) {

    public WorkspaceSearchDocument {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path is required");
        }
        // Missing document content should behave like an empty searchable file.
        markdown = markdown == null ? "" : markdown;
    }
}
