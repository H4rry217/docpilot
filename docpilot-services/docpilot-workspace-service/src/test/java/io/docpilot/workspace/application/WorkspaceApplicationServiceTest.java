package io.docpilot.workspace.application;

import io.docpilot.common.auth.AuthSubject;
import io.docpilot.common.id.SnowflakeIdGenerator;
import io.docpilot.workspace.enums.WorkspaceNodeType;
import io.docpilot.workspace.enums.WorkspaceResourceType;
import io.docpilot.workspace.enums.WorkspaceType;
import io.docpilot.workspace.event.DocumentDeletedEvent;
import io.docpilot.workspace.event.DefaultWorkspaceInitializedEvent;
import io.docpilot.workspace.event.WorkspaceCreatedEvent;
import io.docpilot.workspace.event.WorkspaceDeletedEvent;
import io.docpilot.workspace.event.WorkspaceNodeCreatedEvent;
import io.docpilot.workspace.event.WorkspaceNodeDeletedEvent;
import io.docpilot.workspace.event.WorkspaceNodeMovedEvent;
import io.docpilot.workspace.event.WorkspaceNodeRenamedEvent;
import io.docpilot.workspace.event.WorkspaceRenamedEvent;
import io.docpilot.workspace.model.entity.Workspace;
import io.docpilot.workspace.model.entity.WorkspaceDocument;
import io.docpilot.workspace.model.entity.WorkspaceNode;
import io.docpilot.workspace.model.request.CreateFolderCommand;
import io.docpilot.workspace.model.request.CreateWorkspaceCommand;
import io.docpilot.workspace.model.request.MoveWorkspaceNodeCommand;
import io.docpilot.workspace.model.request.RenameWorkspaceCommand;
import io.docpilot.workspace.model.request.RenameWorkspaceNodeCommand;
import io.docpilot.workspace.processing.WorkspaceIdCodec;
import io.docpilot.workspace.processing.WorkspaceNodeName;
import io.docpilot.workspace.repository.WorkspaceDocumentRepository;
import io.docpilot.workspace.repository.WorkspaceNodeRepository;
import io.docpilot.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceApplicationServiceTest {

    @Test
    void ensureDefaultWorkspacePublishesInitializedEventOnce() {
        Fixture fixture = new Fixture();

        fixture.service.ensureDefaultWorkspace();

        Workspace workspace = fixture.workspaceRepository
                .findActivePersonalByOwnerUserId(fixture.subject.getUserId())
                .orElseThrow();
        assertThat(workspace.getType()).isEqualTo(WorkspaceType.PERSONAL);
        assertThat(fixture.publishedEvents)
                .filteredOn(DefaultWorkspaceInitializedEvent.class::isInstance)
                .singleElement()
                .satisfies(event -> {
                    DefaultWorkspaceInitializedEvent initializedEvent = (DefaultWorkspaceInitializedEvent) event;
                    assertThat(initializedEvent.ownerUserId()).isEqualTo(fixture.subject.getUserId());
                    assertThat(initializedEvent.ownerDisplayName()).isEqualTo(fixture.subject.getDisplayName());
                    assertThat(initializedEvent.workspaceId()).isEqualTo(workspace.getId());
                    assertThat(initializedEvent.rootNodeId()).isEqualTo(workspace.getRootNodeId());
                });

        fixture.service.ensureDefaultWorkspace();

        assertThat(fixture.publishedEvents)
                .filteredOn(DefaultWorkspaceInitializedEvent.class::isInstance)
                .hasSize(1);
    }

    @Test
    void workspaceLifecyclePublishesDomainEvents() {
        Fixture fixture = new Fixture();

        fixture.service.createWorkspace(createWorkspaceCommand("Project"));
        Workspace workspace = fixture.workspaceRepository.findActiveByOwnerUserId(fixture.subject.getUserId()).stream()
                .filter(candidate -> WorkspaceType.CUSTOM == candidate.getType())
                .findFirst()
                .orElseThrow();

        assertThat(fixture.events(WorkspaceCreatedEvent.class))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.ownerUserId()).isEqualTo(fixture.subject.getUserId());
                    assertThat(event.workspaceId()).isEqualTo(workspace.getId());
                    assertThat(event.rootNodeId()).isEqualTo(workspace.getRootNodeId());
                    assertThat(event.workspaceType()).isEqualTo(WorkspaceType.CUSTOM);
                    assertThat(event.name()).isEqualTo("Project");
                });

        fixture.service.renameWorkspace(renameWorkspaceCommand(workspace.getId(), "Project Renamed"));

        assertThat(fixture.events(WorkspaceRenamedEvent.class))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.workspaceId()).isEqualTo(workspace.getId());
                    assertThat(event.oldName()).isEqualTo("Project");
                    assertThat(event.newName()).isEqualTo("Project Renamed");
                });

        fixture.service.renameWorkspace(renameWorkspaceCommand(workspace.getId(), "Project Renamed"));
        assertThat(fixture.events(WorkspaceRenamedEvent.class)).hasSize(1);

        fixture.service.deleteWorkspace(workspace.getId());

        assertThat(fixture.events(WorkspaceDeletedEvent.class))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.ownerUserId()).isEqualTo(fixture.subject.getUserId());
                    assertThat(event.workspaceId()).isEqualTo(workspace.getId());
                    assertThat(event.rootNodeId()).isEqualTo(workspace.getRootNodeId());
                });
        assertThat(fixture.events(WorkspaceNodeDeletedEvent.class))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.workspaceId()).isEqualTo(workspace.getId());
                    assertThat(event.nodeIds()).containsExactly(workspace.getRootNodeId());
                    assertThat(event.documentIds()).isEmpty();
                });
    }

    @Test
    void folderNodeLifecyclePublishesDomainEvents() {
        Fixture fixture = new Fixture();
        fixture.service.ensureDefaultWorkspace();
        Workspace workspace = fixture.workspaceRepository
                .findActivePersonalByOwnerUserId(fixture.subject.getUserId())
                .orElseThrow();
        fixture.publishedEvents.clear();

        fixture.service.createFolder(createFolderCommand(workspace.getId(), workspace.getRootNodeId(), "Folder"));
        WorkspaceNode folder = fixture.nodeRepository.findActiveByWorkspaceIdAndParentNodeIdAndName(
                workspace.getId(), workspace.getRootNodeId(), "Folder").orElseThrow();

        assertThat(fixture.events(WorkspaceNodeCreatedEvent.class))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.ownerUserId()).isEqualTo(fixture.subject.getUserId());
                    assertThat(event.workspaceId()).isEqualTo(workspace.getId());
                    assertThat(event.nodeId()).isEqualTo(folder.getId());
                    assertThat(event.parentNodeId()).isEqualTo(workspace.getRootNodeId());
                    assertThat(event.nodeType()).isEqualTo(WorkspaceNodeType.FOLDER);
                    assertThat(event.resourceType()).isNull();
                    assertThat(event.documentId()).isNull();
                    assertThat(event.name()).isEqualTo("Folder");
                });

        fixture.service.renameNode(renameNodeCommand(folder.getId(), "Renamed Folder"));

        assertThat(fixture.events(WorkspaceNodeRenamedEvent.class))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.nodeId()).isEqualTo(folder.getId());
                    assertThat(event.oldName()).isEqualTo("Folder");
                    assertThat(event.newName()).isEqualTo("Renamed Folder");
                });

        fixture.service.renameNode(renameNodeCommand(folder.getId(), "Renamed Folder"));
        assertThat(fixture.events(WorkspaceNodeRenamedEvent.class)).hasSize(1);

        fixture.service.createFolder(createFolderCommand(workspace.getId(), folder.getId(), "Child"));
        WorkspaceNode child = fixture.nodeRepository.findActiveByWorkspaceIdAndParentNodeIdAndName(
                workspace.getId(), folder.getId(), "Child").orElseThrow();
        List<Long> oldAncestors = new ArrayList<>(child.getAncestors());

        fixture.service.moveNode(moveNodeCommand(child.getId(), workspace.getRootNodeId()));

        assertThat(fixture.events(WorkspaceNodeMovedEvent.class))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.nodeId()).isEqualTo(child.getId());
                    assertThat(event.oldParentNodeId()).isEqualTo(folder.getId());
                    assertThat(event.newParentNodeId()).isEqualTo(workspace.getRootNodeId());
                    assertThat(event.oldAncestors()).isEqualTo(oldAncestors);
                    assertThat(event.newAncestors()).containsExactly(workspace.getRootNodeId());
                });

        fixture.service.moveNode(moveNodeCommand(child.getId(), workspace.getRootNodeId()));
        assertThat(fixture.events(WorkspaceNodeMovedEvent.class)).hasSize(1);

        WorkspaceNode documentNode = fixture.addDocumentNode(workspace, folder, 9001L, "Guide.md");
        fixture.service.deleteNode(folder.getId());

        assertThat(fixture.events(WorkspaceNodeDeletedEvent.class))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.workspaceId()).isEqualTo(workspace.getId());
                    assertThat(event.nodeIds()).containsExactly(folder.getId(), documentNode.getId());
                    assertThat(event.documentIds()).containsExactly(documentNode.getDocumentId());
                });
        assertThat(fixture.events(DocumentDeletedEvent.class))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.workspaceId()).isEqualTo(workspace.getId());
                    assertThat(event.documentId()).isEqualTo(documentNode.getDocumentId());
                    assertThat(event.nodeId()).isEqualTo(documentNode.getId());
                });
        assertThat(fixture.documentRepository.findById(documentNode.getDocumentId()).orElseThrow().getIsDeleted())
                .isTrue();
    }

    private static CreateWorkspaceCommand createWorkspaceCommand(String name) {
        CreateWorkspaceCommand command = new CreateWorkspaceCommand();
        command.setName(name);
        return command;
    }

    private static RenameWorkspaceCommand renameWorkspaceCommand(Long workspaceId, String name) {
        RenameWorkspaceCommand command = new RenameWorkspaceCommand();
        command.setWorkspaceId(workspaceId);
        command.setName(name);
        return command;
    }

    private static CreateFolderCommand createFolderCommand(Long workspaceId, Long parentNodeId, String name) {
        CreateFolderCommand command = new CreateFolderCommand();
        command.setWorkspaceId(workspaceId);
        command.setParentNodeId(parentNodeId);
        command.setName(name);
        return command;
    }

    private static RenameWorkspaceNodeCommand renameNodeCommand(Long nodeId, String name) {
        RenameWorkspaceNodeCommand command = new RenameWorkspaceNodeCommand();
        command.setNodeId(nodeId);
        command.setName(name);
        return command;
    }

    private static MoveWorkspaceNodeCommand moveNodeCommand(Long nodeId, Long parentNodeId) {
        MoveWorkspaceNodeCommand command = new MoveWorkspaceNodeCommand();
        command.setNodeId(nodeId);
        command.setParentNodeId(parentNodeId);
        return command;
    }

    private static class Fixture {

        private final AuthSubject subject = subject();
        private final InMemoryWorkspaceRepository workspaceRepository = new InMemoryWorkspaceRepository();
        private final InMemoryWorkspaceNodeRepository nodeRepository = new InMemoryWorkspaceNodeRepository();
        private final InMemoryWorkspaceDocumentRepository documentRepository = new InMemoryWorkspaceDocumentRepository();
        private final List<Object> publishedEvents = new ArrayList<>();
        private final WorkspaceApplicationService service;

        private Fixture() {
            SnowflakeIdGenerator idGenerator = new SnowflakeIdGenerator(1, 1);
            service = new WorkspaceApplicationService(
                    workspaceRepository,
                    nodeRepository,
                    documentRepository,
                    () -> Optional.of(subject),
                    idGenerator,
                    new WorkspaceIdCodec(),
                    new WorkspaceNodeName(),
                    new NoopWorkspaceTransactionRunner(),
                    new WorkspaceDomainEventPublisher(publishedEvents::add)
            );
        }

        private <T> List<T> events(Class<T> type) {
            return publishedEvents.stream()
                    .filter(type::isInstance)
                    .map(type::cast)
                    .toList();
        }

        private WorkspaceNode addDocumentNode(Workspace workspace, WorkspaceNode parent, Long documentId, String name) {
            WorkspaceDocument document = new WorkspaceDocument();
            document.setId(documentId);
            document.setOwnerUserId(subject.getUserId());
            document.setOriginWorkspaceId(workspace.getId());
            document.setTitle(name);
            document.markCreated();
            documentRepository.save(document);

            WorkspaceNode node = new WorkspaceNode();
            node.setId(documentId + 1L);
            node.setWorkspaceId(workspace.getId());
            node.setParentNodeId(parent.getId());
            node.setAncestors(List.of(workspace.getRootNodeId(), parent.getId()));
            node.setNodeType(WorkspaceNodeType.RESOURCE);
            node.setResourceType(WorkspaceResourceType.DOCUMENT);
            node.setDocumentId(documentId);
            node.setName(name);
            node.markCreated();
            return nodeRepository.save(node);
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

}
