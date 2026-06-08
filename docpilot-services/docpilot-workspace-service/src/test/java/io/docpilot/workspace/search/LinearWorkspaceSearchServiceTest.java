package io.docpilot.workspace.search;

import io.docpilot.filesystem.model.GrepOptions;
import io.docpilot.filesystem.model.GrepResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LinearWorkspaceSearchServiceTest {

    private final LinearWorkspaceSearchService searchService = new LinearWorkspaceSearchService();

    @Test
    void grepScansMarkdownDocumentsByLine() {
        GrepResult result = searchService.grep(List.of(
                new WorkspaceSearchDocument("/a.md", "alpha\nbeta"),
                new WorkspaceSearchDocument("/b.md", "gamma alpha")
        ), "alpha", GrepOptions.unlimited());

        assertThat(result.matches())
                .extracting("path")
                .containsExactly("/a.md", "/b.md");
        assertThat(result.matches())
                .extracting("lineNumber")
                .containsExactly(1L, 1L);
        assertThat(result.truncated()).isFalse();
        assertThat(result.searchedFiles()).isEqualTo(2);
    }

    @Test
    void grepHonorsFileAndMatchLimits() {
        GrepResult fileLimited = searchService.grep(List.of(
                new WorkspaceSearchDocument("/a.md", "alpha"),
                new WorkspaceSearchDocument("/b.md", "alpha")
        ), "alpha", new GrepOptions(1, null));

        assertThat(fileLimited.matches())
                .extracting("path")
                .containsExactly("/a.md");
        assertThat(fileLimited.truncated()).isTrue();
        assertThat(fileLimited.truncationReason()).isEqualTo(GrepResult.TRUNCATED_BY_MAX_FILES);
        assertThat(fileLimited.searchedFiles()).isEqualTo(1);

        GrepResult matchLimited = searchService.grep(List.of(
                new WorkspaceSearchDocument("/a.md", "alpha\nalpha"),
                new WorkspaceSearchDocument("/b.md", "alpha")
        ), "alpha", new GrepOptions(null, 1));

        assertThat(matchLimited.matches()).hasSize(1);
        assertThat(matchLimited.truncated()).isTrue();
        assertThat(matchLimited.truncationReason()).isEqualTo(GrepResult.TRUNCATED_BY_MAX_MATCHES);
        assertThat(matchLimited.searchedFiles()).isEqualTo(1);
    }
}
