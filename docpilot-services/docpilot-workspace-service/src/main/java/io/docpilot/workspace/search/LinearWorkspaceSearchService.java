package io.docpilot.workspace.search;

import io.docpilot.filesystem.model.GrepMatch;
import io.docpilot.filesystem.model.GrepOptions;
import io.docpilot.filesystem.model.GrepResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple line-by-line workspace search implementation.
 */
public class LinearWorkspaceSearchService implements WorkspaceSearchService {

    @Override
    public GrepResult grep(List<WorkspaceSearchDocument> documents, String text, GrepOptions options) {
        GrepOptions grepOptions = GrepOptions.effective(options);
        List<GrepMatch> matches = new ArrayList<>();
        long searchedFiles = 0L;
        String truncationReason = null;

        for (WorkspaceSearchDocument document : documents == null ? List.<WorkspaceSearchDocument>of() : documents) {
            // Budget checks happen before reading a document so searchedFiles reports actual work done.
            if (grepOptions.isMaxFilesReached(searchedFiles)) {
                truncationReason = GrepResult.TRUNCATED_BY_MAX_FILES;
                break;
            }
            if (grepOptions.isMaxMatchesReached(matches.size())) {
                truncationReason = GrepResult.TRUNCATED_BY_MAX_MATCHES;
                break;
            }

            searchedFiles++;
            if (grepDocument(document, text, matches, grepOptions)) {
                truncationReason = GrepResult.TRUNCATED_BY_MAX_MATCHES;
                break;
            }
        }

        return new GrepResult(matches, truncationReason != null, truncationReason, 0L, searchedFiles);
    }

    private boolean grepDocument(WorkspaceSearchDocument document, String text, List<GrepMatch> matches, GrepOptions options) {
        String[] lines = document.markdown().split("\\R", -1);

        for (int index = 0; index < lines.length; index++) {
            // Grep v1 intentionally keeps literal contains semantics; ranking/indexing can replace this service later.
            if (lines[index].contains(text)) {
                matches.add(new GrepMatch(document.path(), index + 1L, lines[index]));
                if (options.isMaxMatchesReached(matches.size())) {
                    return true;
                }
            }
        }

        return false;
    }
}
