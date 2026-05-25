package io.docpilot;

import io.docpilot.document.application.WorkspaceManager;
import io.docpilot.filesystem.FilesystemDefaultPaths;
import io.docpilot.filesystem.PathMappingService;
import io.docpilot.filesystem.provider.ProviderRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DocPilotApplicationTest {

    @Autowired
    private ProviderRegistry providerRegistry;

    @Autowired
    private PathMappingService pathMappingService;

    @Autowired
    private WorkspaceManager workspaceManager;

    @Test
    void contextLoads() {
    }

    @Test
    void filesystemDefaultsToLocalProviderAndProjectPathMapping() {
        assertThat(providerRegistry.findById("local")).isPresent();

        var workspace = workspaceManager.listMyWorkspaces().getFirst();

        assertThat(pathMappingService.list(workspace.getWorkspaceId()))
                .anySatisfy(mapping -> {
                    assertThat(mapping.getVirtualPath()).isEqualTo(FilesystemDefaultPaths.PROJECT_VIRTUAL_PATH);
                    assertThat(mapping.getProviderId()).isEqualTo("local");
                    assertThat(mapping.getProviderRoot()).isEqualTo(FilesystemDefaultPaths.workspaceProjectProviderRoot(workspace.getWorkspaceId()));
                });
    }

}
