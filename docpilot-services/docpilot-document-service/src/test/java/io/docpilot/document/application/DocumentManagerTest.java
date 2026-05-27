package io.docpilot.document.application;

import io.docpilot.common.auth.AuthContextProvider;
import io.docpilot.common.auth.AuthSubject;
import io.docpilot.document.auth.DefaultDocumentAccessAuthorizer;
import io.docpilot.document.exception.DocumentAccessDeniedException;
import io.docpilot.document.exception.DocumentVersionConflictException;
import io.docpilot.document.model.DocPilotDocument;
import io.docpilot.document.model.DocumentDetail;
import io.docpilot.document.model.DocumentState;
import io.docpilot.document.model.CreateDocumentCommand;
import io.docpilot.document.model.DocumentVisibility;
import io.docpilot.document.model.UpdateDocumentContentCommand;
import io.docpilot.document.repository.DocumentRepository;
import io.docpilot.user.model.UserInformation;
import io.docpilot.user.provider.UserInformationProvider;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentManagerTest {

    @Test
    void createDocumentForCurrentSubject() {
        InMemoryDocumentRepository repository = new InMemoryDocumentRepository();
        DocumentManager manager = manager(repository, subject(1L, "Alice"), userInformation(1L, "Alice"));

        CreateDocumentCommand command = new CreateDocumentCommand();
        command.setTitle("  Plan  ");
        command.setMarkdown("# Hello");

        DocPilotDocument document = manager.createDocument(command);

        assertThat(document.getDocumentId()).matches("[0-9a-f]{32}");
        assertThat(document.getOwnerUserId()).isEqualTo(1L);
        assertThat(document.getTitle()).isEqualTo("Plan");
        assertThat(document.getVersion()).isEqualTo(1);
        assertThat(document.getBlockDocument().getBlocks()).hasSize(1);
    }

    @Test
    void readDocumentDetailWithOwnerProfile() {
        InMemoryDocumentRepository repository = new InMemoryDocumentRepository();
        DocumentManager manager = manager(repository, subject(1L, "Alice"), userInformation(1L, "Alice"));

        DocPilotDocument document = manager.createDocument(new CreateDocumentCommand());

        DocumentDetail detail = manager.getDocumentDetail(document.getDocumentId());

        assertThat(detail.getDocument().getDocumentId()).isEqualTo(document.getDocumentId());
        assertThat(detail.getOwnerInformation().getDisplayName()).isEqualTo("Alice");
    }

    @Test
    void rejectWriteFromNonOwner() {
        InMemoryDocumentRepository repository = new InMemoryDocumentRepository();
        DocumentManager ownerManager = manager(repository, subject(1L, "Alice"), userInformation(1L, "Alice"));
        DocPilotDocument document = ownerManager.createDocument(new CreateDocumentCommand());

        DocumentManager otherManager = manager(repository, subject(2L, "Bob"), userInformation(2L, "Bob"));
        UpdateDocumentContentCommand command = new UpdateDocumentContentCommand();
        command.setMarkdown("# Changed");

        assertThatThrownBy(() -> otherManager.replaceContent(document.getDocumentId(), command))
                .isInstanceOf(DocumentAccessDeniedException.class);
    }

    @Test
    void allowAnonymousReadForLinkReadableDocument() {
        InMemoryDocumentRepository repository = new InMemoryDocumentRepository();
        DocumentManager ownerManager = manager(repository, subject(1L, "Alice"), userInformation(1L, "Alice"));

        CreateDocumentCommand command = new CreateDocumentCommand();
        command.setVisibility(DocumentVisibility.LINK_READ);
        DocPilotDocument document = ownerManager.createDocument(command);

        DocumentManager anonymousManager = manager(repository, null, null);

        assertThat(anonymousManager.getDocument(document.getDocumentId()).getDocumentId()).isEqualTo(document.getDocumentId());
    }

    @Test
    void rejectStaleVersionUpdate() {
        InMemoryDocumentRepository repository = new InMemoryDocumentRepository();
        DocumentManager manager = manager(repository, subject(1L, "Alice"), userInformation(1L, "Alice"));
        DocPilotDocument document = manager.createDocument(new CreateDocumentCommand());

        UpdateDocumentContentCommand command = new UpdateDocumentContentCommand();
        command.setMarkdown("# Changed");
        command.setExpectedVersion(99L);

        assertThatThrownBy(() -> manager.replaceContent(document.getDocumentId(), command))
                .isInstanceOf(DocumentVersionConflictException.class);
    }

    @Test
    void softDeleteDocument() {
        InMemoryDocumentRepository repository = new InMemoryDocumentRepository();
        DocumentManager manager = manager(repository, subject(1L, "Alice"), userInformation(1L, "Alice"));
        DocPilotDocument document = manager.createDocument(new CreateDocumentCommand());

        DocPilotDocument deleted = manager.deleteDocument(document.getDocumentId());

        assertThat(deleted.getState()).isEqualTo(DocumentState.DELETED);
        assertThat(deleted.getVersion()).isEqualTo(2);
    }

    private DocumentManager manager(InMemoryDocumentRepository repository, AuthSubject subject, UserInformation userInformation) {
        AuthContextProvider authContextProvider = () -> Optional.ofNullable(subject);
        UserInformationProvider userInformationProvider = userId -> userInformation != null && userInformation.getUserId().equals(userId)
                ? Optional.of(userInformation)
                : Optional.empty();
        return new DocumentManager(repository, authContextProvider, new DefaultDocumentAccessAuthorizer(), userInformationProvider);
    }

    private AuthSubject subject(Long userId, String displayName) {
        AuthSubject subject = new AuthSubject();
        subject.setUserId(userId);
        subject.setDisplayName(displayName);
        return subject;
    }

    private UserInformation userInformation(Long userId, String displayName) {
        UserInformation userInformation = new UserInformation();
        userInformation.setUserId(userId);
        userInformation.setDisplayName(displayName);
        return userInformation;
    }

    private static final class InMemoryDocumentRepository implements DocumentRepository {

        private final Map<String, DocPilotDocument> documents = new HashMap<>();

        @Override
        public DocPilotDocument save(DocPilotDocument document) {
            documents.put(document.getDocumentId(), document);
            return document;
        }

        @Override
        public Optional<DocPilotDocument> findById(String documentId) {
            return Optional.ofNullable(documents.get(documentId));
        }

    }

}
