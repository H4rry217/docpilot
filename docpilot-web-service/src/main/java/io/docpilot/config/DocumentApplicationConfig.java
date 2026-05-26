package io.docpilot.config;

import io.docpilot.common.auth.AuthContextProvider;
import io.docpilot.document.application.DocumentManager;
import io.docpilot.document.application.WorkspaceManager;
import io.docpilot.document.auth.DefaultDocumentAccessAuthorizer;
import io.docpilot.document.auth.DocumentAccessAuthorizer;
import io.docpilot.document.processing.WorkspaceIdGenerator;
import io.docpilot.document.processing.WorkspaceNodeIdGenerator;
import io.docpilot.document.repository.DocumentRepository;
import io.docpilot.document.repository.WorkspaceNodeRepository;
import io.docpilot.document.repository.WorkspaceRepository;
import io.docpilot.user.provider.UserInformationProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Configuration
public class DocumentApplicationConfig {

    @Bean
    public DocumentAccessAuthorizer documentAccessAuthorizer() {
        return new DefaultDocumentAccessAuthorizer();
    }

    @Bean
    public DocumentManager documentManager(DocumentRepository documentRepository,
                                           AuthContextProvider authContextProvider,
                                           DocumentAccessAuthorizer accessAuthorizer,
                                           UserInformationProvider userInformationProvider) {
        return new DocumentManager(documentRepository, authContextProvider, accessAuthorizer, userInformationProvider);
    }

    @Bean
    public WorkspaceManager workspaceManager(WorkspaceRepository workspaceRepository,
                                             WorkspaceNodeRepository workspaceNodeRepository,
                                             AuthContextProvider authContextProvider,
                                             ObjectProvider<Consumer<io.docpilot.document.model.Workspace>> workspaceCreatedListener) {
        return new WorkspaceManager(workspaceRepository, workspaceNodeRepository, authContextProvider,
                new WorkspaceIdGenerator(),
                new WorkspaceNodeIdGenerator(),
                java.time.Clock.systemDefaultZone(),
                workspaceCreatedListener.getIfAvailable(() -> workspace -> {}));
    }

}
