package io.docpilot.workspace.model;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceModelNamingTest {

    @Test
    void dtoClassesUseDtoSuffix() throws IOException {
        Path dtoDirectory = Path.of("src/main/java/io/docpilot/workspace/model/dto");
        if (!Files.exists(dtoDirectory)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(dtoDirectory)) {
            List<String> invalidClassNames = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .map(path -> path.getFileName().toString())
                    .filter(fileName -> !fileName.endsWith("Dto.java"))
                    .toList();

            assertThat(invalidClassNames).isEmpty();
        }
    }

}
