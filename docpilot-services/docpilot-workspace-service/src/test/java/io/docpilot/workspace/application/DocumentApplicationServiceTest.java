package io.docpilot.workspace.application;

import io.docpilot.block.model.BlockDocument;
import io.docpilot.common.auth.AuthSubject;
import io.docpilot.common.id.SnowflakeIdGenerator;
import io.docpilot.workspace.enums.WorkspaceNodeType;
import io.docpilot.workspace.enums.WorkspaceResourceType;
import io.docpilot.workspace.enums.WorkspaceType;
import io.docpilot.workspace.event.DocumentContentChangedEvent;
import io.docpilot.workspace.event.DocumentCreatedEvent;
import io.docpilot.workspace.event.WorkspaceNodeCreatedEvent;
import io.docpilot.workspace.model.entity.DocumentRevision;
import io.docpilot.workspace.model.entity.Workspace;
import io.docpilot.workspace.model.entity.WorkspaceDocument;
import io.docpilot.workspace.model.entity.WorkspaceNode;
import io.docpilot.workspace.model.request.CreateDocumentCommand;
import io.docpilot.workspace.model.request.SaveDocumentContentCommand;
import io.docpilot.workspace.processing.WorkspaceIdCodec;
import io.docpilot.workspace.processing.WorkspaceNodeName;
import io.docpilot.workspace.repository.DocumentRevisionRepository;
import io.docpilot.workspace.repository.WorkspaceDocumentRepository;
import io.docpilot.workspace.repository.WorkspaceNodeRepository;
import io.docpilot.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentApplicationServiceTest {

    @Test
    void createDocumentPublishesDomainEvents() {
        Fixture fixture = new Fixture();
        Workspace workspace = fixture.ensureDefaultWorkspace();
        fixture.publishedEvents.clear();

        fixture.service.createDocument(createDocumentCommand(workspace, "Guide", "Guide.md", "# Guide"));

        WorkspaceNode node = fixture.nodeRepository
                .findActiveByWorkspaceIdAndParentNodeIdAndName(workspace.getId(), workspace.getRootNodeId(), "Guide.md")
                .orElseThrow();
        WorkspaceDocument document = fixture.documentRepository.findById(node.getDocumentId()).orElseThrow();

        assertThat(fixture.events(WorkspaceNodeCreatedEvent.class))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.ownerUserId()).isEqualTo(fixture.subject.getUserId());
                    assertThat(event.workspaceId()).isEqualTo(workspace.getId());
                    assertThat(event.nodeId()).isEqualTo(node.getId());
                    assertThat(event.parentNodeId()).isEqualTo(workspace.getRootNodeId());
                    assertThat(event.nodeType()).isEqualTo(WorkspaceNodeType.RESOURCE);
                    assertThat(event.resourceType()).isEqualTo(WorkspaceResourceType.DOCUMENT);
                    assertThat(event.documentId()).isEqualTo(document.getId());
                    assertThat(event.name()).isEqualTo("Guide.md");
                });
        assertThat(fixture.events(DocumentCreatedEvent.class))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.ownerUserId()).isEqualTo(fixture.subject.getUserId());
                    assertThat(event.workspaceId()).isEqualTo(workspace.getId());
                    assertThat(event.nodeId()).isEqualTo(node.getId());
                    assertThat(event.documentId()).isEqualTo(document.getId());
                    assertThat(event.revisionId()).isEqualTo(document.getCurrentRevisionId());
                    assertThat(event.version()).isEqualTo(1L);
                    assertThat(event.title()).isEqualTo("Guide");
                });
        assertThat(fixture.events(DocumentContentChangedEvent.class))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.ownerUserId()).isEqualTo(fixture.subject.getUserId());
                    assertThat(event.workspaceId()).isEqualTo(workspace.getId());
                    assertThat(event.documentId()).isEqualTo(document.getId());
                    assertThat(event.revisionId()).isEqualTo(document.getCurrentRevisionId());
                    assertThat(event.version()).isEqualTo(1L);
                    assertThat(event.baseVersion()).isEqualTo(0L);
                    assertThat(event.clientMutationId()).isNull();
                });
    }

    @Test
    void saveContentPublishesChangeAndIdempotentRetryDoesNotRepublish() {
        Fixture fixture = new Fixture();
        Workspace workspace = fixture.ensureDefaultWorkspace();
        fixture.service.createDocument(createDocumentCommand(workspace, "Guide", "Guide.md", "# Guide"));
        WorkspaceNode node = fixture.nodeRepository
                .findActiveByWorkspaceIdAndParentNodeIdAndName(workspace.getId(), workspace.getRootNodeId(), "Guide.md")
                .orElseThrow();
        WorkspaceDocument document = fixture.documentRepository.findById(node.getDocumentId()).orElseThrow();
        fixture.publishedEvents.clear();

        SaveDocumentContentCommand command = saveCommand(document.getId(), 1L, "mutation-1");
        fixture.service.saveContent(command);

        WorkspaceDocument saved = fixture.documentRepository.findById(document.getId()).orElseThrow();
        assertThat(fixture.events(DocumentContentChangedEvent.class))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.workspaceId()).isEqualTo(workspace.getId());
                    assertThat(event.documentId()).isEqualTo(document.getId());
                    assertThat(event.revisionId()).isEqualTo(saved.getCurrentRevisionId());
                    assertThat(event.version()).isEqualTo(2L);
                    assertThat(event.baseVersion()).isEqualTo(1L);
                    assertThat(event.clientMutationId()).isEqualTo("mutation-1");
                });

        fixture.service.saveContent(command);

        assertThat(fixture.events(DocumentContentChangedEvent.class)).hasSize(1);
    }

    private static CreateDocumentCommand createDocumentCommand(Workspace workspace, String title, String nodeName, String markdown) {
        CreateDocumentCommand command = new CreateDocumentCommand();
        command.setWorkspaceId(workspace.getId());
        command.setParentNodeId(workspace.getRootNodeId());
        command.setTitle(title);
        command.setNodeName(nodeName);
        command.setMarkdown(markdown);
        return command;
    }

    private static SaveDocumentContentCommand saveCommand(Long documentId, Long baseVersion, String clientMutationId) {
        SaveDocumentContentCommand command = new SaveDocumentContentCommand();
        command.setDocumentId(documentId);
        command.setBaseVersion(baseVersion);
        command.setBlockDocument(new BlockDocument());
        command.setClientMutationId(clientMutationId);
        return command;
    }

    private static class Fixture {

        private final AuthSubject subject = subject();
        private final InMemoryWorkspaceRepository workspaceRepository = new InMemoryWorkspaceRepository();
        private final InMemoryWorkspaceNodeRepository nodeRepository = new InMemoryWorkspaceNodeRepository();
        private final InMemoryWorkspaceDocumentRepository documentRepository = new InMemoryWorkspaceDocumentRepository();
        private final InMemoryDocumentRevisionRepository revisionRepository = new InMemoryDocumentRevisionRepository();
        private final List<Object> publishedEvents = new ArrayList<>();
        private final WorkspaceApplicationService workspaceService;
        private final DocumentApplicationService service;

        private Fixture() {
            SnowflakeIdGenerator idGenerator = new SnowflakeIdGenerator(1, 1);
            WorkspaceIdCodec idCodec = new WorkspaceIdCodec();
            WorkspaceNodeName nodeName = new WorkspaceNodeName();
            WorkspaceDomainEventPublisher eventPublisher = new WorkspaceDomainEventPublisher(publishedEvents::add);
            workspaceService = new WorkspaceApplicationService(
                    workspaceRepository,
                    nodeRepository,
                    documentRepository,
                    () -> Optional.of(subject),
                    idGenerator,
                    idCodec,
                    nodeName,
                    new NoopWorkspaceTransactionRunner(),
                    eventPublisher
            );
            service = new DocumentApplicationService(
                    workspaceService,
                    nodeRepository,
                    documentRepository,
                    revisionRepository,
                    () -> Optional.of(subject),
                    idGenerator,
                    idCodec,
                    nodeName,
                    new NoopWorkspaceTransactionRunner(),
                    eventPublisher
            );
        }

        private Workspace ensureDefaultWorkspace() {
            workspaceService.ensureDefaultWorkspace();
            return workspaceRepository.findActivePersonalByOwnerUserId(subject.getUserId()).orElseThrow();
        }

        private <T> List<T> events(Class<T> type) {
            return publishedEvents.stream()
                    .filter(type::isInstance)
                    .map(type::cast)
                    .toList();
        }

        private static AuthSubject subject() {
            AuthSubject subject = new AuthSubject();
            subject.setUserId(42L);
            subject.setDisplayName("Ada");
            return subject;
        }

    }

    private static class InMemoryWorkspaceRepository implements WorkspaceRepository {

        private final Map<Long, Workspace> workspaces = new LinkedHashMap<>();

        @Override
        public Workspace save(Workspace workspace) {
            workspaces.put(workspace.getId(), workspace);
            return workspace;
        }

        @Override
        public Optional<Workspace> findById(Long workspaceId) {
            return Optional.ofNullable(workspaces.get(workspaceId));
        }

        @Override
        public Optional<Workspace> findActivePersonalByOwnerUserId(Long ownerUserId) {
            return workspaces.values().stream()
                    .filter(workspace -> ownerUserId.equals(workspace.getOwnerUserId()))
                    .filter(workspace -> WorkspaceType.PERSONAL == workspace.getType())
                    .filter(workspace -> !Boolean.TRUE.equals(workspace.getIsDeleted()))
                    .findFirst();
        }

        @Override
        public List<Workspace> findActiveByOwnerUserId(Long ownerUserId) {
            return workspaces.values().stream()
                    .filter(workspace -> ownerUserId.equals(workspace.getOwnerUserId()))
                    .filter(workspace -> !Boolean.TRUE.equals(workspace.getIsDeleted()))
                    .toList();
        }

    }

    private static class InMemoryWorkspaceNodeRepository implements WorkspaceNodeRepository {

        private final Map<Long, WorkspaceNode> nodes = new LinkedHashMap<>();

        @Override
        public WorkspaceNode save(WorkspaceNode node) {
            nodes.put(node.getId(), node);
            return node;
        }

        @Override
        public List<WorkspaceNode> saveAll(Collection<WorkspaceNode> nodes) {
            nodes.forEach(this::save);
            return new ArrayList<>(nodes);
        }

        @Override
        public Optional<WorkspaceNode> findById(Long nodeId) {
            return Optional.ofNullable(nodes.get(nodeId));
        }

        @Override
        public List<WorkspaceNode> findActiveByWorkspaceId(Long workspaceId) {
            return nodes.values().stream()
                    .filter(node -> workspaceId.equals(node.getWorkspaceId()))
                    .filter(node -> !Boolean.TRUE.equals(node.getIsDeleted()))
                    .toList();
        }

        @Override
        public Optional<WorkspaceNode> findActiveByWorkspaceIdAndParentNodeIdAndName(
                Long workspaceId,
                Long parentNodeId,
                String name
        ) {
            return nodes.values().stream()
                    .filter(node -> workspaceId.equals(node.getWorkspaceId()))
                    .filter(node -> parentNodeId.equals(node.getParentNodeId()))
                    .filter(node -> name.equals(node.getName()))
                    .filter(node -> !Boolean.TRUE.equals(node.getIsDeleted()))
                    .findFirst();
        }

        @Override
        public List<WorkspaceNode> findActiveByWorkspaceIdAndAncestor(Long workspaceId, Long ancestorNodeId) {
            return nodes.values().stream()
                    .filter(node -> workspaceId.equals(node.getWorkspaceId()))
                    .filter(node -> node.getAncestors().contains(ancestorNodeId))
                    .filter(node -> !Boolean.TRUE.equals(node.getIsDeleted()))
                    .toList();
        }

    }

    private static class InMemoryWorkspaceDocumentRepository implements WorkspaceDocumentRepository {

        private final Map<Long, WorkspaceDocument> documents = new LinkedHashMap<>();

        @Override
        public WorkspaceDocument save(WorkspaceDocument document) {
            documents.put(document.getId(), document);
            return document;
        }

        @Override
        public Optional<WorkspaceDocument> findById(Long documentId) {
            return Optional.ofNullable(documents.get(documentId));
        }

    }

    private static class InMemoryDocumentRevisionRepository implements DocumentRevisionRepository {

        private final Map<Long, DocumentRevision> revisions = new LinkedHashMap<>();

        @Override
        public DocumentRevision save(DocumentRevision revision) {
            revisions.put(revision.getId(), revision);
            return revision;
        }

        @Override
        public Optional<DocumentRevision> findById(Long revisionId) {
            return Optional.ofNullable(revisions.get(revisionId));
        }

        @Override
        public Optional<DocumentRevision> findByDocumentIdAndClientMutationId(Long documentId, String clientMutationId) {
            return revisions.values().stream()
                    .filter(revision -> documentId.equals(revision.getDocumentId()))
                    .filter(revision -> clientMutationId.equals(revision.getClientMutationId()))
                    .findFirst();
        }

        @Override
        public List<DocumentRevision> findByDocumentIdOrderByVersionDesc(Long documentId, int limit) {
            return revisions.values().stream()
                    .filter(revision -> documentId.equals(revision.getDocumentId()))
                    .sorted(Comparator.comparing(DocumentRevision::getVersion).reversed())
                    .limit(limit)
                    .collect(Collectors.toList());
        }

    }

}
