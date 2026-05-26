package io.docpilot.filesystem;

import io.docpilot.filesystem.exception.InvalidPathException;
import io.docpilot.filesystem.model.PathMapping;
import io.docpilot.filesystem.store.InMemoryPathMappingStore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PathMappingServiceTest {

    @Test
    void resolvesByLongestVirtualPathPrefix() {
        PathMappingService service = new PathMappingService(new InMemoryPathMappingStore());
        service.create(mapping("ws1", "/project", "s3", "workspaces/ws1/project"));
        service.create(mapping("ws1", "/project/tmp", "local", "tmp/ws1"));

        var resolved = service.resolve("ws1", "//project/tmp/result.txt");

        assertThat(resolved.mapping().getProviderId()).isEqualTo("local");
        assertThat(resolved.relativePath()).isEqualTo("result.txt");
        assertThat(resolved.providerPath()).isEqualTo("tmp/ws1/result.txt");
    }

    @Test
    void rejectsDuplicateVirtualPathInsideWorkspace() {
        PathMappingService service = new PathMappingService(new InMemoryPathMappingStore());
        service.create(mapping("ws1", "/project", "s3", "a"));

        assertThatThrownBy(() -> service.create(mapping("ws1", "project/", "local", "b")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void rejectsParentTraversal() {
        PathMappingService service = new PathMappingService(new InMemoryPathMappingStore());
        service.create(mapping("ws1", "/project", "s3", "workspaces/ws1/project"));

        assertThatThrownBy(() -> service.resolve("ws1", "/project/../secret.txt"))
                .isInstanceOf(InvalidPathException.class);
    }

    private PathMapping mapping(String workspaceId, String virtualPath, String providerId, String providerRoot) {
        PathMapping mapping = new PathMapping();
        mapping.setWorkspaceId(workspaceId);
        mapping.setVirtualPath(virtualPath);
        mapping.setProviderId(providerId);
        mapping.setProviderRoot(providerRoot);
        mapping.setEnabled(true);
        return mapping;
    }

}
