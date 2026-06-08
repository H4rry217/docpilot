package io.docpilot.workspace.search;

import io.docpilot.filesystem.model.GrepOptions;
import io.docpilot.filesystem.model.GrepResult;

import java.util.List;

/**
 * Search boundary for workspace document content.
 */
public interface WorkspaceSearchService {

    /**
     * Finds text matches across the supplied workspace documents using the provided grep limits.
     *
     * @param documents searchable documents already scoped by the caller.
     * @param text literal text to find.
     * @param options max-file and max-match controls.
     * @return grep result containing matches and truncation metadata.
     */
    GrepResult grep(List<WorkspaceSearchDocument> documents, String text, GrepOptions options);
}
