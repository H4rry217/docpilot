package io.docpilot.document.application;

import io.docpilot.document.exception.DocumentAccessDeniedException;
import io.docpilot.document.exception.DocumentNotFoundException;
import io.docpilot.document.model.DocPilotDocument;
import io.docpilot.document.model.DocumentRole;
import io.docpilot.document.model.DocumentShare;
import io.docpilot.document.model.DocumentShareState;
import io.docpilot.document.model.ShareDocumentCommand;
import io.docpilot.document.processing.DocumentShareIdGenerator;
import io.docpilot.document.repository.DocumentRepository;
import io.docpilot.document.repository.DocumentShareRepository;
import io.docpilot.user.auth.AuthContextProvider;
import io.docpilot.user.auth.AuthSubject;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Application boundary for file-level document sharing.
 */
public class DocumentShareManager {

    private final DocumentRepository documentRepository;
    private final DocumentShareRepository documentShareRepository;
    private final AuthContextProvider authContextProvider;
    private final DocumentShareIdGenerator shareIdGenerator;
    private final Clock clock;

    public DocumentShareManager(DocumentRepository documentRepository,
                                DocumentShareRepository documentShareRepository,
                                AuthContextProvider authContextProvider) {
        this(documentRepository, documentShareRepository, authContextProvider, new DocumentShareIdGenerator(), Clock.systemDefaultZone());
    }

    public DocumentShareManager(DocumentRepository documentRepository,
                                DocumentShareRepository documentShareRepository,
                                AuthContextProvider authContextProvider,
                                DocumentShareIdGenerator shareIdGenerator,
                                Clock clock) {
        this.documentRepository = documentRepository;
        this.documentShareRepository = documentShareRepository;
        this.authContextProvider = authContextProvider;
        this.shareIdGenerator = shareIdGenerator;
        this.clock = clock;
    }

    /**
     * Shares a document with a target user.
     */
    public DocumentShare shareDocument(ShareDocumentCommand command) {
        AuthSubject subject = requireSubject();
        DocPilotDocument document = requireOwnedDocument(command.getDocumentId(), subject);
        Instant now = clock.instant();

        DocumentShare share = new DocumentShare();
        share.setShareId(shareIdGenerator.nextId());
        share.setDocumentId(document.getDocumentId());
        share.setOwnerUserId(subject.getUserId());
        share.setTargetUserId(command.getTargetUserId());
        share.setRole(command.getRole() == null ? DocumentRole.VIEWER : command.getRole());
        share.setState(DocumentShareState.ACTIVE);
        share.setCreateTime(now);
        share.setUpdateTime(now);
        return documentShareRepository.save(share);
    }

    /**
     * Lists document shares received by the current authenticated subject.
     */
    public List<DocumentShare> listSharedWithMe() {
        AuthSubject subject = requireSubject();
        return documentShareRepository.findByTargetUserId(subject.getUserId());
    }

    private AuthSubject requireSubject() {
        return authContextProvider.currentSubject()
                .orElseThrow(() -> new DocumentAccessDeniedException("Authentication is required"));
    }

    private DocPilotDocument requireOwnedDocument(String documentId, AuthSubject subject) {
        DocPilotDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found: " + documentId));
        if (!Objects.equals(document.getOwnerUserId(), subject.getUserId())) {
            throw new DocumentAccessDeniedException("Document access denied");
        }
        return document;
    }

}
