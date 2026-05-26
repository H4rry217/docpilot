package io.docpilot.document.application;

import io.docpilot.block.processing.MarkdownBlockParser;
import io.docpilot.document.auth.DocumentAccessAuthorizer;
import io.docpilot.document.exception.DocumentAccessDeniedException;
import io.docpilot.document.exception.DocumentNotFoundException;
import io.docpilot.document.exception.DocumentVersionConflictException;
import io.docpilot.document.model.DocPilotDocument;
import io.docpilot.document.model.DocumentDetail;
import io.docpilot.document.model.DocumentState;
import io.docpilot.document.model.CreateDocumentCommand;
import io.docpilot.document.model.DocumentAction;
import io.docpilot.document.model.DocumentVisibility;
import io.docpilot.document.model.UpdateDocumentContentCommand;
import io.docpilot.document.processing.DocumentIdGenerator;
import io.docpilot.document.repository.DocumentRepository;
import io.docpilot.user.auth.AuthContextProvider;
import io.docpilot.user.auth.AuthSubject;
import io.docpilot.user.provider.UserInformationProvider;

import java.time.Clock;
import java.time.Instant;

/**
 * Application boundary for document content operations.
 */
public class DocumentManager {

    private final DocumentRepository documentRepository;
    private final AuthContextProvider authContextProvider;
    private final DocumentAccessAuthorizer accessAuthorizer;
    private final UserInformationProvider userInformationProvider;
    private final MarkdownBlockParser markdownBlockParser;
    private final DocumentIdGenerator documentIdGenerator;
    private final Clock clock;

    public DocumentManager(DocumentRepository documentRepository,
                                AuthContextProvider authContextProvider,
                                DocumentAccessAuthorizer accessAuthorizer,
                                UserInformationProvider userInformationProvider) {
        this(documentRepository, authContextProvider, accessAuthorizer, userInformationProvider,
                new MarkdownBlockParser(), new DocumentIdGenerator(), Clock.systemDefaultZone());
    }

    public DocumentManager(DocumentRepository documentRepository,
                                AuthContextProvider authContextProvider,
                                DocumentAccessAuthorizer accessAuthorizer,
                                UserInformationProvider userInformationProvider,
                                MarkdownBlockParser markdownBlockParser,
                                DocumentIdGenerator documentIdGenerator,
                                Clock clock) {
        this.documentRepository = documentRepository;
        this.authContextProvider = authContextProvider;
        this.accessAuthorizer = accessAuthorizer;
        this.userInformationProvider = userInformationProvider;
        this.markdownBlockParser = markdownBlockParser;
        this.documentIdGenerator = documentIdGenerator;
        this.clock = clock;
    }

    /**
     * Creates a document content aggregate.
     *
     * Workspace placement is represented by WorkspaceNode and is not managed here.
     */
    public DocPilotDocument createDocument(CreateDocumentCommand command) {
        AuthSubject subject = requireSubject();
        Instant now = clock.instant();
        String markdown = command.getMarkdown() == null ? "" : command.getMarkdown();

        DocPilotDocument document = new DocPilotDocument();
        document.setDocumentId(documentIdGenerator.nextId());
        document.setOwnerUserId(subject.getUserId());
        document.setTitle(normalizeTitle(command.getTitle()));
        document.setMarkdown(markdown);
        document.setBlockDocument(markdownBlockParser.parse(markdown));
        document.setVisibility(command.getVisibility() == null ? DocumentVisibility.PRIVATE : command.getVisibility());
        document.setState(DocumentState.ACTIVE);
        document.setVersion(1);
        document.setCreateTime(now);
        document.setUpdateTime(now);

        return documentRepository.save(document);
    }

    /**
     * Reads a document after checking document read permission.
     */
    public DocPilotDocument getDocument(String documentId) {
        DocPilotDocument document = requireDocument(documentId);
        authorize(authContextProvider.currentSubject().orElse(null), document, DocumentAction.READ);
        return document;
    }

    /**
     * Reads a document and enriches it with owner data when available.
     */
    public DocumentDetail getDocumentDetail(String documentId) {
        DocPilotDocument document = getDocument(documentId);
        DocumentDetail detail = new DocumentDetail();
        detail.setDocument(document);
        detail.setOwnerInformation(userInformationProvider.findByUserId(document.getOwnerUserId()).orElse(null));
        return detail;
    }

    /**
     * Replaces the full Markdown content and reparses the block document.
     */
    public DocPilotDocument replaceContent(String documentId, UpdateDocumentContentCommand command) {
        DocPilotDocument document = requireDocument(documentId);
        AuthSubject subject = requireSubject();
        authorize(subject, document, DocumentAction.WRITE);
        checkVersion(document, command.getExpectedVersion());

        String markdown = command.getMarkdown() == null ? "" : command.getMarkdown();
        document.setMarkdown(markdown);
        document.setBlockDocument(markdownBlockParser.parse(markdown));
        document.setVersion(document.getVersion() + 1);
        document.setUpdateTime(clock.instant());
        return documentRepository.save(document);
    }

    /**
     * Soft-deletes a document after checking delete permission.
     */
    public DocPilotDocument deleteDocument(String documentId) {
        DocPilotDocument document = requireDocument(documentId);
        AuthSubject subject = requireSubject();
        authorize(subject, document, DocumentAction.DELETE);

        document.setState(DocumentState.DELETED);
        document.setVersion(document.getVersion() + 1);
        document.setUpdateTime(clock.instant());
        return documentRepository.save(document);
    }

    private AuthSubject requireSubject() {
        return authContextProvider.currentSubject()
                .orElseThrow(() -> new DocumentAccessDeniedException("Authentication is required"));
    }

    private DocPilotDocument requireDocument(String documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found: " + documentId));
    }

    private void authorize(AuthSubject subject, DocPilotDocument document, DocumentAction action) {
        if (!accessAuthorizer.canAccess(subject, document, action)) {
            throw new DocumentAccessDeniedException("Document access denied");
        }
    }

    private void checkVersion(DocPilotDocument document, Long expectedVersion) {
        if (expectedVersion != null && expectedVersion != document.getVersion()) {
            throw new DocumentVersionConflictException("Document version conflict");
        }
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return "Untitled";
        }
        return title.strip();
    }

}
