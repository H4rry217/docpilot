package io.docpilot.config;

import io.docpilot.document.application.DocumentManager;
import io.docpilot.document.application.WorkspaceManager;
import io.docpilot.document.auth.AuthContextProvider;
import io.docpilot.document.auth.AuthSubject;
import io.docpilot.document.auth.DefaultDocumentAccessAuthorizer;
import io.docpilot.document.auth.DocumentAccessAuthorizer;
import io.docpilot.document.processing.WorkspaceIdGenerator;
import io.docpilot.document.processing.WorkspaceNodeIdGenerator;
import io.docpilot.document.repository.DocumentRepository;
import io.docpilot.document.repository.WorkspaceNodeRepository;
import io.docpilot.document.repository.WorkspaceRepository;
import io.docpilot.document.user.UserProfile;
import io.docpilot.document.user.UserProfileProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;
import java.util.function.Consumer;

@Configuration
public class DocumentApplicationConfig {

    public static final String DEV_USER_ID = "dev-user";
    public static final String DEV_DISPLAY_NAME = "HarryZ";

    @Bean
    public AuthContextProvider authContextProvider() {
        return () -> {
            AuthSubject subject = new AuthSubject();
            subject.setUserId(DEV_USER_ID);
            subject.setDisplayName(DEV_DISPLAY_NAME);
            return Optional.of(subject);
        };
    }

    @Bean
    public UserProfileProvider userProfileProvider() {
        return userId -> {
            if (!DEV_USER_ID.equals(userId)) {
                return Optional.empty();
            }
            UserProfile profile = new UserProfile();
            profile.setUserId(DEV_USER_ID);
            profile.setDisplayName(DEV_DISPLAY_NAME);
            profile.setEmail("dev-user@docpilot.local");
            return Optional.of(profile);
        };
    }

    @Bean
    public DocumentAccessAuthorizer documentAccessAuthorizer() {
        return new DefaultDocumentAccessAuthorizer();
    }

    @Bean
    public DocumentManager documentManager(DocumentRepository documentRepository,
                                           AuthContextProvider authContextProvider,
                                           DocumentAccessAuthorizer accessAuthorizer,
                                           UserProfileProvider userProfileProvider) {
        return new DocumentManager(documentRepository, authContextProvider, accessAuthorizer, userProfileProvider);
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
