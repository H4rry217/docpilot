package io.docpilot.filesystem;

import io.docpilot.filesystem.exception.ReadonlyPathException;
import io.docpilot.filesystem.model.PathMapping;
import io.docpilot.filesystem.provider.DefaultProviderRegistry;
import io.docpilot.filesystem.provider.LocalFilesystemProvider;
import io.docpilot.filesystem.store.InMemoryPathMappingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FilesystemServiceTest {

    Path tempDir;

    private FilesystemService filesystemService;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory(Path.of("target"), "filesystem-service-");
        PathMappingService pathMappingService = new PathMappingService(new InMemoryPathMappingStore());
        pathMappingService.create(mapping("/project", "primary", "workspaces/ws1/project", false));
        pathMappingService.create(mapping("/tmp", "scratch", "scratch/ws1", false));
        pathMappingService.create(mapping("/readonly", "primary", "readonly/ws1", true));

        DefaultProviderRegistry registry = new DefaultProviderRegistry();
        registry.register(new LocalFilesystemProvider("primary", tempDir.resolve("primary")));
        registry.register(new LocalFilesystemProvider("scratch", tempDir.resolve("scratch")));
        filesystemService = new FilesystemService(pathMappingService, registry);
    }

    @Test
    void readsWritesListsAndStatsUsingVirtualPaths() {
        filesystemService.writeText("ws1", "/project/docs/a.md", "hello");

        assertThat(filesystemService.readText("ws1", "/project/docs/a.md")).isEqualTo("hello");
        assertThat(filesystemService.exists("ws1", "/project/docs/a.md")).isTrue();
        assertThat(filesystemService.list("ws1", "/project/docs"))
                .extracting("path")
                .containsExactly("/project/docs/a.md");
        assertThat(filesystemService.stat("ws1", "/project/docs/a.md").path())
                .isEqualTo("/project/docs/a.md");
    }

    @Test
    void movesAcrossProvidersByCopyThenDelete() {
        filesystemService.writeText("ws1", "/project/a.txt", "hello");

        filesystemService.move("ws1", "/project/a.txt", "/tmp/a.txt");

        assertThat(filesystemService.exists("ws1", "/project/a.txt")).isFalse();
        assertThat(filesystemService.readText("ws1", "/tmp/a.txt")).isEqualTo("hello");
    }

    @Test
    void rejectsWritesThroughReadonlyMapping() {
        assertThatThrownBy(() -> filesystemService.writeText("ws1", "/readonly/a.txt", "no"))
                .isInstanceOf(ReadonlyPathException.class);
    }

    private PathMapping mapping(String virtualPath, String providerId, String providerRoot, boolean readonly) {
        PathMapping mapping = new PathMapping();
        mapping.setWorkspaceId("ws1");
        mapping.setVirtualPath(virtualPath);
        mapping.setProviderId(providerId);
        mapping.setProviderRoot(providerRoot);
        mapping.setReadonly(readonly);
        mapping.setEnabled(true);
        return mapping;
    }

}
