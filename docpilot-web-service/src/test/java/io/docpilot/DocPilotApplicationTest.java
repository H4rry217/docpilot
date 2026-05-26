package io.docpilot;

import io.docpilot.common.auth.AuthSubject;
import io.docpilot.common.auth.AuthSubjectContext;
import io.docpilot.config.UserApplicationConfig;
import io.docpilot.document.application.WorkspaceManager;
import io.docpilot.document.model.CreateWorkspaceCommand;
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
        AuthSubject subject = new AuthSubject();
        subject.setUserId(UserApplicationConfig.DEV_USER_ID);
        subject.setDisplayName(UserApplicationConfig.DEV_DISPLAY_NAME);

        AuthSubjectContext.runAs(subject, () -> {
            assertThat(providerRegistry.findById("local")).isPresent();

            CreateWorkspaceCommand command = new CreateWorkspaceCommand();
            command.setName("Filesystem Test Workspace");
            var workspace = workspaceManager.createWorkspace(command);

            assertThat(pathMappingService.list(workspace.getWorkspaceId()))
                    .anySatisfy(mapping -> {
                        assertThat(mapping.getVirtualPath()).isEqualTo(FilesystemDefaultPaths.PROJECT_VIRTUAL_PATH);
                        assertThat(mapping.getProviderId()).isEqualTo("local");
                        assertThat(mapping.getProviderRoot()).isEqualTo(FilesystemDefaultPaths.workspaceProjectProviderRoot(workspace.getWorkspaceId()));
                    });
        });
    }

}
