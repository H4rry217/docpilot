package io.docpilot.filesystem;

import io.docpilot.filesystem.model.GrepOptions;
import io.docpilot.filesystem.model.GrepResult;
import io.docpilot.filesystem.provider.LocalFilesystemProvider;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderFilesystemTest {

    @Test
    void adaptsLocalProviderToPathFirstFilesystem() throws IOException {
        Path tempDir = Files.createTempDirectory(Path.of("target"), "provider-filesystem-");
        Filesystem filesystem = new ProviderFilesystem(new LocalFilesystemProvider("local", tempDir));

        filesystem.writeText("/project/docs/a.md", "hello\nworld");

        assertThat(filesystem.readText("/project/docs/a.md")).isEqualTo("hello\nworld");
        assertThat(filesystem.list("/project/docs"))
                .extracting("path")
                .containsExactly("/project/docs/a.md");
        assertThat(filesystem.stat("/project/docs/a.md").path()).isEqualTo("/project/docs/a.md");
        assertThat(filesystem.glob("/project/**/*.md"))
                .extracting("path")
                .containsExactly("/project/docs/a.md");
        assertThat(filesystem.grep("/project", "world"))
                .extracting("path")
                .containsExactly("/project/docs/a.md");

        GrepResult result = filesystem.grep("/project", "world", new GrepOptions(1, 1));
        assertThat(result.matches())
                .extracting("path")
                .containsExactly("/project/docs/a.md");
        assertThat(result.truncated()).isTrue();
        assertThat(result.truncationReason()).isEqualTo(GrepResult.TRUNCATED_BY_MAX_MATCHES);
        assertThat(result.searchedFiles()).isEqualTo(1);
    }

}
