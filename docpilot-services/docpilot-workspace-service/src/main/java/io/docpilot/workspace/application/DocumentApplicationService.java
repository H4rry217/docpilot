package io.docpilot.workspace.application;

import io.docpilot.block.model.BlockDocument;
import io.docpilot.block.processing.MarkdownBlockParser;
import io.docpilot.block.processing.MarkdownBlockRenderer;
import io.docpilot.block.processing.ProseMirrorJsonConverter;
import io.docpilot.common.auth.AuthContextProvider;
import io.docpilot.common.auth.AuthSubject;
import io.docpilot.common.exception.ConflictException;
import io.docpilot.common.exception.ForbiddenException;
import io.docpilot.common.exception.NotFoundException;
import io.docpilot.common.exception.UnauthorizedException;
import io.docpilot.common.id.SnowflakeIdGenerator;
import io.docpilot.workspace.model.request.CreateDocumentCommand;
import io.docpilot.workspace.model.entity.DocumentRevision;
import io.docpilot.workspace.model.request.SaveDocumentContentCommand;
import io.docpilot.workspace.model.entity.Workspace;
import io.docpilot.workspace.model.entity.WorkspaceDocument;
import io.docpilot.workspace.model.entity.WorkspaceDocument.DocumentContent;
import io.docpilot.workspace.model.entity.WorkspaceNode;
import io.docpilot.workspace.enums.WorkspaceNodeType;
import io.docpilot.workspace.enums.WorkspaceResourceType;
import io.docpilot.workspace.model.response.DocumentContentResponse;
import io.docpilot.workspace.model.response.DocumentDetailResponse;
import io.docpilot.workspace.model.response.DocumentResponse;
import io.docpilot.workspace.model.response.DocumentRevisionListResponse;
import io.docpilot.workspace.model.response.DocumentRevisionResponse;
import io.docpilot.workspace.processing.WorkspaceIdCodec;
import io.docpilot.workspace.processing.WorkspaceNodeName;
import io.docpilot.workspace.repository.DocumentRevisionRepository;
import io.docpilot.workspace.repository.WorkspaceDocumentRepository;
import io.docpilot.workspace.repository.WorkspaceNodeRepository;
import lombok.Setter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

@Setter
public class DocumentApplicationService {

    private WorkspaceApplicationService workspaceService;
    private WorkspaceNodeRepository nodeRepository;
    private WorkspaceDocumentRepository documentRepository;
    private DocumentRevisionRepository revisionRepository;
    private AuthContextProvider authContextProvider;
    private SnowflakeIdGenerator idGenerator;
    private WorkspaceIdCodec idCodec;
    private WorkspaceNodeName workspaceNodeName;
    private WorkspaceTransactionRunner transactionRunner;
    private MarkdownBlockParser markdownBlockParser;
    private MarkdownBlockRenderer markdownBlockRenderer;
    private ProseMirrorJsonConverter proseMirrorJsonConverter = new ProseMirrorJsonConverter();
    private Clock clock = Clock.systemDefaultZone();

    public DocumentDetailResponse createDocument(CreateDocumentCommand command) {
        AuthSubject subject = requireSubject();
        WorkspaceDocument savedDocument = transactionRunner.run(() -> {
            Workspace workspace = workspaceService.requireOwnedWorkspace(command.getWorkspaceId());
            WorkspaceNode parent = workspaceService.requireActiveNode(command.getParentNodeId() == null
                    ? workspace.getRootNodeId()
                    : command.getParentNodeId());
            if (!Objects.equals(parent.getWorkspaceId(), workspace.getId())) {
                throw new ForbiddenException("Workspace node access denied");
            }
            if (!parent.isFolder()) {
                throw new IllegalArgumentException("Parent node must be a folder");
            }

            String title = normalizeTitle(command.getTitle());
            String nodeName = workspaceNodeName.normalizeName(command.getNodeName() == null ? title + ".md" : command.getNodeName());
            nodeRepository.findActiveByWorkspaceIdAndParentNodeIdAndName(
                    workspace.getId(), parent.getId(), nodeName).ifPresent(existing -> {
                throw new ConflictException("A node with the same name already exists");
            });

            long documentId = idGenerator.nextId();
            long revisionId = idGenerator.nextId();
            BlockDocument blockDocument = initialBlockDocument(command);
            String markdown = markdownBlockRenderer.render(blockDocument);
            String checksum = checksum(markdown);

            WorkspaceDocument document = new WorkspaceDocument();
            document.setId(documentId);
            document.setOwnerUserId(subject.getUserId());
            document.setOriginWorkspaceId(workspace.getId());
            document.setTitle(title);
            document.setCurrentVersion(1L);
            document.setCurrentRevisionId(revisionId);
            document.setContent(content(blockDocument, markdown, checksum));
            document.markCreated();

            DocumentRevision revision = revision(revisionId, documentId, 1L, 0L, subject.getUserId(), blockDocument, markdown, checksum);

            WorkspaceNode node = new WorkspaceNode();
            node.setId(idGenerator.nextId());
            node.setWorkspaceId(workspace.getId());
            node.setParentNodeId(parent.getId());
            node.setAncestors(childAncestors(parent));
            node.setNodeType(WorkspaceNodeType.RESOURCE);
            node.setResourceType(WorkspaceResourceType.DOCUMENT);
            node.setDocumentId(documentId);
            node.setName(nodeName);
            node.markCreated();

            documentRepository.save(document);
            revisionRepository.save(revision);
            nodeRepository.save(node);
            return document;
        });
        return toDetailResponse(savedDocument);
    }

    public DocumentDetailResponse getDocument(Long documentId) {
        WorkspaceDocument document = requireActiveDocument(documentId);
        workspaceService.requireOwnedWorkspace(document.getOriginWorkspaceId());
        return toDetailResponse(document);
    }

    public DocumentDetailResponse saveContent(SaveDocumentContentCommand command) {
        AuthSubject subject = requireSubject();
        WorkspaceDocument savedDocument = transactionRunner.run(() -> {
            WorkspaceDocument document = requireActiveDocument(command.getDocumentId());
            workspaceService.requireOwnedWorkspace(document.getOriginWorkspaceId());
            if (!Objects.equals(document.getOwnerUserId(), subject.getUserId())) {
                throw new ForbiddenException("Document access denied");
            }
            if (!Objects.equals(document.getCurrentVersion(), command.getBaseVersion())) {
                throw new ConflictException("Document version conflict. Current version: " + document.getCurrentVersion());
            }

            long nextVersion = document.getCurrentVersion() + 1L;
            long revisionId = idGenerator.nextId();
            BlockDocument blockDocument = command.getBlockDocument() == null ? new BlockDocument() : command.getBlockDocument();
            String markdown = markdownBlockRenderer.render(blockDocument);
            String checksum = checksum(markdown);

            DocumentRevision revision = revision(
                    revisionId,
                    document.getId(),
                    nextVersion,
                    document.getCurrentVersion(),
                    subject.getUserId(),
                    blockDocument,
                    markdown,
                    checksum
            );
            revisionRepository.save(revision);

            document.setCurrentVersion(nextVersion);
            document.setCurrentRevisionId(revisionId);
            document.setContent(content(blockDocument, markdown, checksum));
            document.markUpdated();
            return documentRepository.save(document);
        });
        return toDetailResponse(savedDocument);
    }

    public DocumentRevisionListResponse listRevisions(Long documentId, int limit) {
        WorkspaceDocument document = requireActiveDocument(documentId);
        workspaceService.requireOwnedWorkspace(document.getOriginWorkspaceId());
        return new DocumentRevisionListResponse(revisionRepository
                .findByDocumentIdOrderByVersionDesc(document.getId(), Math.max(1, Math.min(limit, 100)))
                .stream()
                .map(this::toResponse)
                .toList());
    }

    private WorkspaceDocument requireActiveDocument(Long documentId) {
        if (documentId == null || documentId <= 0) {
            throw new IllegalArgumentException("documentId is required");
        }
        WorkspaceDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new NotFoundException("Document not found: " + documentId));
        if (Boolean.TRUE.equals(document.getIsDeleted())) {
            throw new NotFoundException("Document not found: " + documentId);
        }
        return document;
    }

    private AuthSubject requireSubject() {
        return authContextProvider.currentSubject()
                .orElseThrow(() -> new UnauthorizedException("Authentication is required"));
    }

    private BlockDocument initialBlockDocument(CreateDocumentCommand command) {
        if (command.getBlockDocument() != null) {
            return command.getBlockDocument();
        }
        return markdownBlockParser.parse(command.getMarkdown() == null ? "" : command.getMarkdown());
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return "Untitled";
        }
        return title.strip();
    }

    private DocumentContent content(BlockDocument blockDocument, String markdown, String checksum) {
        DocumentContent content = new DocumentContent();
        content.setBlockSchemaVersion(blockDocument.getSchemaVersion());
        content.setBlockDocument(blockDocument);
        content.setMarkdownText(markdown);
        content.setChecksum(checksum);
        return content;
    }

    private DocumentRevision revision(long revisionId,
                                      long documentId,
                                      long version,
                                      long baseVersion,
                                      long authorUserId,
                                      BlockDocument blockDocument,
                                      String markdown,
                                      String checksum) {
        DocumentRevision revision = new DocumentRevision();
        revision.setId(revisionId);
        revision.setDocumentId(documentId);
        revision.setVersion(version);
        revision.setBaseVersion(baseVersion);
        revision.setAuthorUserId(authorUserId);
        revision.setSnapshot(blockDocument);
        revision.setMarkdownSnapshot(markdown);
        revision.setChecksum(checksum);
        revision.markCreated();
        return revision;
    }

    private List<Long> childAncestors(WorkspaceNode parent) {
        List<Long> ancestors = new ArrayList<>(parent.getAncestors());
        ancestors.add(parent.getId());
        return ancestors;
    }

    private String checksum(String markdown) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + HexFormat.of().formatHex(digest.digest(markdown.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private DocumentDetailResponse toDetailResponse(WorkspaceDocument document) {
        return new DocumentDetailResponse(
                toResponse(document),
                proseMirrorJsonConverter.toProseMirror(document.getContent().getBlockDocument())
        );
    }

    private DocumentResponse toResponse(WorkspaceDocument document) {
        return new DocumentResponse(
                idCodec.format(document.getId()),
                idCodec.format(document.getOwnerUserId()),
                idCodec.format(document.getOriginWorkspaceId()),
                document.getTitle(),
                idCodec.format(document.getCurrentVersion()),
                idCodec.format(document.getCurrentRevisionId()),
                document.getMetadata(),
                toResponse(document.getContent()),
                format(document.getCreateTime()),
                format(document.getUpdateTime())
        );
    }

    private DocumentContentResponse toResponse(DocumentContent content) {
        return new DocumentContentResponse(
                content.getBlockSchemaVersion(),
                content.getBlockDocument(),
                content.getMarkdownText(),
                content.getChecksum()
        );
    }

    private DocumentRevisionResponse toResponse(DocumentRevision revision) {
        return new DocumentRevisionResponse(
                idCodec.format(revision.getId()),
                idCodec.format(revision.getDocumentId()),
                idCodec.format(revision.getVersion()),
                idCodec.format(revision.getBaseVersion()),
                idCodec.format(revision.getAuthorUserId()),
                revision.getSnapshot(),
                revision.getMarkdownSnapshot(),
                revision.getChecksum(),
                format(revision.getCreateTime())
        );
    }

    private String format(LocalDateTime instant) {
        return instant == null ? null : instant.toString();
    }

}


