package io.docpilot.document.application;

import io.docpilot.document.auth.AuthContextProvider;
import io.docpilot.document.auth.AuthSubject;
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
import io.docpilot.document.user.UserProfile;
import io.docpilot.document.user.UserProfileProvider;
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
        DocumentManager manager = manager(repository, subject("u1", "Alice"), profile("u1", "Alice"));

        CreateDocumentCommand command = new CreateDocumentCommand();
        command.setTitle("  Plan  ");
        command.setMarkdown("# Hello");

        DocPilotDocument document = manager.createDocument(command);

        assertThat(document.getDocumentId()).matches("[0-9a-f]{32}");
        assertThat(document.getOwnerUserId()).isEqualTo("u1");
        assertThat(document.getTitle()).isEqualTo("Plan");
        assertThat(document.getVersion()).isEqualTo(1);
        assertThat(document.getBlockDocument().getBlocks()).hasSize(1);
    }

    @Test
    void readDocumentDetailWithOwnerProfile() {
        InMemoryDocumentRepository repository = new InMemoryDocumentRepository();
        DocumentManager manager = manager(repository, subject("u1", "Alice"), profile("u1", "Alice"));

        DocPilotDocument document = manager.createDocument(new CreateDocumentCommand());

        DocumentDetail detail = manager.getDocumentDetail(document.getDocumentId());

        assertThat(detail.getDocument().getDocumentId()).isEqualTo(document.getDocumentId());
        assertThat(detail.getOwnerProfile().getDisplayName()).isEqualTo("Alice");
    }

    @Test
    void rejectWriteFromNonOwner() {
        InMemoryDocumentRepository repository = new InMemoryDocumentRepository();
        DocumentManager ownerManager = manager(repository, subject("u1", "Alice"), profile("u1", "Alice"));
        DocPilotDocument document = ownerManager.createDocument(new CreateDocumentCommand());

        DocumentManager otherManager = manager(repository, subject("u2", "Bob"), profile("u2", "Bob"));
        UpdateDocumentContentCommand command = new UpdateDocumentContentCommand();
        command.setMarkdown("# Changed");

        assertThatThrownBy(() -> otherManager.replaceContent(document.getDocumentId(), command))
                .isInstanceOf(DocumentAccessDeniedException.class);
    }

    @Test
    void allowAnonymousReadForLinkReadableDocument() {
        InMemoryDocumentRepository repository = new InMemoryDocumentRepository();
        DocumentManager ownerManager = manager(repository, subject("u1", "Alice"), profile("u1", "Alice"));

        CreateDocumentCommand command = new CreateDocumentCommand();
        command.setVisibility(DocumentVisibility.LINK_READ);
        DocPilotDocument document = ownerManager.createDocument(command);

        DocumentManager anonymousManager = manager(repository, null, null);

        assertThat(anonymousManager.getDocument(document.getDocumentId()).getDocumentId()).isEqualTo(document.getDocumentId());
    }

    @Test
    void rejectStaleVersionUpdate() {
        InMemoryDocumentRepository repository = new InMemoryDocumentRepository();
        DocumentManager manager = manager(repository, subject("u1", "Alice"), profile("u1", "Alice"));
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
        DocumentManager manager = manager(repository, subject("u1", "Alice"), profile("u1", "Alice"));
        DocPilotDocument document = manager.createDocument(new CreateDocumentCommand());

        DocPilotDocument deleted = manager.deleteDocument(document.getDocumentId());

        assertThat(deleted.getState()).isEqualTo(DocumentState.DELETED);
        assertThat(deleted.getVersion()).isEqualTo(2);
    }

    private DocumentManager manager(InMemoryDocumentRepository repository, AuthSubject subject, UserProfile profile) {
        AuthContextProvider authContextProvider = () -> Optional.ofNullable(subject);
        UserProfileProvider userProfileProvider = userId -> profile != null && profile.getUserId().equals(userId)
                ? Optional.of(profile)
                : Optional.empty();
        return new DocumentManager(repository, authContextProvider, new DefaultDocumentAccessAuthorizer(), userProfileProvider);
    }

    private AuthSubject subject(String userId, String displayName) {
        AuthSubject subject = new AuthSubject();
        subject.setUserId(userId);
        subject.setDisplayName(displayName);
        return subject;
    }

    private UserProfile profile(String userId, String displayName) {
        UserProfile profile = new UserProfile();
        profile.setUserId(userId);
        profile.setDisplayName(displayName);
        return profile;
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
