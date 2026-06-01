package io.docpilot.workspace.filesystem;

import io.docpilot.filesystem.CompositeFilesystem;
import io.docpilot.filesystem.Filesystem;
import io.docpilot.filesystem.model.FileEntry;
import io.docpilot.workspace.enums.WorkspaceNodeType;
import io.docpilot.workspace.enums.WorkspaceResourceType;
import io.docpilot.workspace.model.entity.Workspace;
import io.docpilot.workspace.model.entity.WorkspaceDocument;
import io.docpilot.workspace.model.entity.WorkspaceNode;
import io.docpilot.workspace.repository.WorkspaceDocumentRepository;
import io.docpilot.workspace.repository.WorkspaceNodeRepository;
import io.docpilot.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceFilesystemTest {

    private InMemoryWorkspaceRepository workspaceRepository;
    private InMemoryWorkspaceNodeRepository nodeRepository;
    private InMemoryWorkspaceDocumentRepository documentRepository;
    private WorkspaceFilesystem workspaceFilesystem;

    @BeforeEach
    void setUp() {
        workspaceRepository = new InMemoryWorkspaceRepository();
        nodeRepository = new InMemoryWorkspaceNodeRepository();
        documentRepository = new InMemoryWorkspaceDocumentRepository();
        addWorkspace(1L, 7L, "Alpha", "hello\nalpha");
        addWorkspace(2L, 8L, "Beta", "hidden");

        workspaceFilesystem = new WorkspaceFilesystem(1L, workspaceRepository, nodeRepository, documentRepository);
    }

    @Test
    void workspaceFilesystemMapsOneWorkspaceTreeToReadonlyFiles() {
        assertThat(workspaceFilesystem.list("/"))
                .extracting(FileEntry::path)
                .containsExactly("/docs");
        assertThat(workspaceFilesystem.list("/docs"))
                .extracting(FileEntry::path)
                .containsExactly("/docs/a.md");
        assertThat(workspaceFilesystem.readText("/docs/a.md")).isEqualTo("hello\nalpha");
        assertThat(workspaceFilesystem.glob("/docs/*.md"))
                .extracting(FileEntry::path)
                .containsExactly("/docs/a.md");
        assertThat(workspaceFilesystem.grep("/", "alpha"))
                .extracting("path")
                .containsExactly("/docs/a.md");
    }

    @Test
    void workspaceFilesystemCanBeMountedUnderProjectWorkspaceId() {
        Filesystem workspaceRoots = new CompositeFilesystem()
                .mount("/workspace/1", workspaceFilesystem);
        Filesystem projectFilesystem = new CompositeFilesystem()
                .mount("/project", workspaceRoots);

        assertThat(projectFilesystem.list("/project"))
                .extracting(FileEntry::path)
                .containsExactly("/project/workspace");
        assertThat(projectFilesystem.list("/project/workspace"))
                .extracting(FileEntry::path)
                .containsExactly("/project/workspace/1");
        assertThat(projectFilesystem.readText("/project/workspace/1/docs/a.md")).isEqualTo("hello\nalpha");
        assertThat(projectFilesystem.grep("/project/workspace/1", "alpha"))
                .extracting("path")
                .containsExactly("/project/workspace/1/docs/a.md");
    }

    private void addWorkspace(Long workspaceId, Long ownerUserId, String name, String markdown) {
        Workspace workspace = new Workspace();
        workspace.setId(workspaceId);
        workspace.setOwnerUserId(ownerUserId);
        workspace.setName(name);
        workspace.setRootNodeId(workspaceId * 100);
        workspace.markCreated();
        workspaceRepository.save(workspace);

        WorkspaceNode root = folder(workspace.getRootNodeId(), workspaceId, 0L, List.of(), name);
        WorkspaceNode docs = folder(workspaceId * 100 + 1, workspaceId, root.getId(), List.of(root.getId()), "docs");
        WorkspaceNode documentNode = new WorkspaceNode();
        documentNode.setId(workspaceId * 100 + 2);
        documentNode.setWorkspaceId(workspaceId);
        documentNode.setParentNodeId(docs.getId());
        documentNode.setAncestors(List.of(root.getId(), docs.getId()));
        documentNode.setNodeType(WorkspaceNodeType.RESOURCE);
        documentNode.setResourceType(WorkspaceResourceType.DOCUMENT);
        documentNode.setDocumentId(workspaceId * 1000);
        documentNode.setName("a.md");
        documentNode.markCreated();
        nodeRepository.save(root);
        nodeRepository.save(docs);
        nodeRepository.save(documentNode);

        WorkspaceDocument document = new WorkspaceDocument();
        document.setId(documentNode.getDocumentId());
        document.setOwnerUserId(ownerUserId);
        document.setOriginWorkspaceId(workspaceId);
        document.setTitle("A");
        WorkspaceDocument.DocumentContent content = new WorkspaceDocument.DocumentContent();
        content.setMarkdownText(markdown);
        document.setContent(content);
        document.markCreated();
        documentRepository.save(document);
    }

    private WorkspaceNode folder(Long id, Long workspaceId, Long parentNodeId, List<Long> ancestors, String name) {
        WorkspaceNode node = new WorkspaceNode();
        node.setId(id);
        node.setWorkspaceId(workspaceId);
        node.setParentNodeId(parentNodeId);
        node.setAncestors(ancestors);
        node.setNodeType(WorkspaceNodeType.FOLDER);
        node.setName(name);
        node.markCreated();
        return node;
    }

    private static class InMemoryWorkspaceRepository implements WorkspaceRepository {
        private final Map<Long, Workspace> workspaces = new HashMap<>();

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
            return Optional.empty();
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
        private final Map<Long, WorkspaceNode> nodes = new HashMap<>();

        @Override
        public WorkspaceNode save(WorkspaceNode node) {
            nodes.put(node.getId(), node);
            return node;
        }

        @Override
        public List<WorkspaceNode> saveAll(Collection<WorkspaceNode> nodes) {
            nodes.forEach(this::save);
            return List.copyOf(nodes);
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
        public Optional<WorkspaceNode> findActiveByWorkspaceIdAndParentNodeIdAndName(Long workspaceId, Long parentNodeId, String name) {
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
        private final Map<Long, WorkspaceDocument> documents = new HashMap<>();

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
