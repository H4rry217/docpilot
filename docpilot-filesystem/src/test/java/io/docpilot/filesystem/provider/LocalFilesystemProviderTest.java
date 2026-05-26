package io.docpilot.filesystem.provider;

import io.docpilot.filesystem.exception.InvalidPathException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFilesystemProviderTest {

    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory(Path.of("target"), "local-provider-");
    }

    @Test
    void readsWritesListsAndGrepsFilesInsideRoot() {
        LocalFilesystemProvider provider = new LocalFilesystemProvider("local", tempDir);

        provider.write("project/docs/a.md", "hello\nworld".getBytes());

        assertThat(new String(provider.read("project/docs/a.md"))).isEqualTo("hello\nworld");
        assertThat(provider.list("project/docs"))
                .extracting("path")
                .containsExactly("project/docs/a.md");
        assertThat(provider.glob("project/**/*.md"))
                .extracting("path")
                .containsExactly("project/docs/a.md");
        assertThat(provider.grep("project", "world"))
                .singleElement()
                .satisfies(match -> {
                    assertThat(match.path()).isEqualTo("project/docs/a.md");
                    assertThat(match.lineNumber()).isEqualTo(2);
                });
    }

    @Test
    void rejectsPathsEscapingRoot() {
        LocalFilesystemProvider provider = new LocalFilesystemProvider("local", tempDir);

        assertThatThrownBy(() -> provider.write("../outside.txt", "nope".getBytes()))
                .isInstanceOf(InvalidPathException.class);
    }

}
