package io.docpilot.workspace.filesystem;

import io.docpilot.common.auth.AuthContextProvider;
import io.docpilot.common.auth.AuthSubject;
import io.docpilot.common.exception.ForbiddenException;
import io.docpilot.filesystem.retrieval.FilesystemRetrievalHit;
import io.docpilot.workspace.enums.WorkspaceNodeType;
import io.docpilot.workspace.enums.WorkspaceResourceType;
import io.docpilot.workspace.knowledge.KnowledgeRetrievalProvider;
import io.docpilot.workspace.knowledge.KnowledgeRetrievalService;
import io.docpilot.workspace.knowledge.config.KnowledgeProperties;
import io.docpilot.workspace.knowledge.model.KnowledgeIndexedChunk;
import io.docpilot.workspace.knowledge.model.KnowledgeRetrievalRequest;
import io.docpilot.workspace.knowledge.model.KnowledgeRetrievalResult;
import io.docpilot.workspace.model.entity.Workspace;
import io.docpilot.workspace.model.entity.WorkspaceDocument;
import io.docpilot.workspace.model.entity.WorkspaceNode;
import io.docpilot.workspace.model.request.UserFilesystemRetrieveCommand;
import io.docpilot.workspace.model.response.UserFilesystemRetrieveResponse;
import io.docpilot.workspace.processing.WorkspaceIdCodec;
import io.docpilot.workspace.repository.WorkspaceDocumentRepository;
import io.docpilot.workspace.repository.WorkspaceNodeRepository;
import io.docpilot.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserFilesystemServiceTest {

    private InMemoryWorkspaceRepository workspaceRepository;
    private InMemoryWorkspaceNodeRepository nodeRepository;
    private InMemoryWorkspaceDocumentRepository documentRepository;
    private CapturingRetrievalProvider retrievalProvider;
    private UserFilesystemService service;

    @BeforeEach
    void setUp() {
        workspaceRepository = new InMemoryWorkspaceRepository();
        nodeRepository = new InMemoryWorkspaceNodeRepository();
        documentRepository = new InMemoryWorkspaceDocumentRepository();
        retrievalProvider = new CapturingRetrievalProvider();
        service = new UserFilesystemService(
                workspaceRepository,
                nodeRepository,
                documentRepository,
                authContext(7L),
                new WorkspaceIdCodec(),
                retrievalService(retrievalProvider)
        );

        addWorkspace(1L, 7L, "Alpha");
        addWorkspace(2L, 8L, "Beta");
    }

    @Test
    void retrievesOneOwnedWorkspacePathAndMapsHitsToUserPath() {
        UserFilesystemRetrieveResponse response = service.retrieve(command(
                "/workspace/1/docs/a.md",
                "alpha",
                UserFilesystemFailureMode.BEST_EFFORT
        ));

        assertThat(retrievalProvider.requests).hasSize(1);
        KnowledgeRetrievalRequest providerRequest = retrievalProvider.requests.getFirst();
        assertThat(providerRequest.getWorkspaceId()).isEqualTo(1L);
        assertThat(providerRequest.getOwnerUserId()).isEqualTo(7L);
        assertThat(providerRequest.getPath()).isEqualTo("/docs/a.md");
        assertThat(providerRequest.getScopeDocumentIds()).containsExactly(1000L);
        assertThat(providerRequest.getPreferredDocumentId()).isEqualTo(1000L);
        assertThat(response.hits())
                .extracting(FilesystemRetrievalHit::path)
                .containsExactly("/workspace/1/docs/a.md");
        assertThat(response.diagnostics()).isEmpty();
    }

    @Test
    void workspaceNamespaceOnlySearchesCurrentUserWorkspaces() {
        UserFilesystemRetrieveResponse response = service.retrieve(command(
                "/workspace",
                "alpha",
                UserFilesystemFailureMode.BEST_EFFORT
        ));

        assertThat(retrievalProvider.requests)
                .extracting(KnowledgeRetrievalRequest::getWorkspaceId)
                .containsExactly(1L);
        assertThat(response.hits())
                .extracting(FilesystemRetrievalHit::path)
                .containsExactly("/workspace/1/docs/a.md");
    }

    @Test
    void stopsSearchingRemainingWorkspacesWhenTopKIsSatisfied() {
        addWorkspace(3L, 7L, "Gamma");
        addWorkspace(4L, 7L, "Delta");
        UserFilesystemRetrieveCommand command = command(
                "/workspace",
                "alpha",
                UserFilesystemFailureMode.BEST_EFFORT
        );
        command.setTopK(1);

        UserFilesystemRetrieveResponse response = service.retrieve(command);

        assertThat(retrievalProvider.requests)
                .extracting(KnowledgeRetrievalRequest::getWorkspaceId)
                .containsExactly(1L);
        assertThat(retrievalProvider.requests.getFirst().getLimit()).isEqualTo(1);
        assertThat(response.hits()).hasSize(1);
        assertThat(response.truncated()).isTrue();
        assertThat(response.truncationReason()).isEqualTo("topK");
        assertThat(response.searchedMounts()).isEqualTo(1L);
    }

    @Test
    void bestEffortKeepsSuccessfulHitsAndReturnsDiagnosticsForFailedWorkspace() {
        addWorkspace(3L, 7L, "Gamma");
        retrievalProvider.failingWorkspaceIds.add(3L);

        UserFilesystemRetrieveResponse response = service.retrieve(command(
                "/workspace",
                "alpha",
                UserFilesystemFailureMode.BEST_EFFORT
        ));

        assertThat(response.hits())
                .extracting(FilesystemRetrievalHit::path)
                .containsExactly("/workspace/1/docs/a.md");
        assertThat(response.diagnostics()).hasSize(1);
        assertThat(response.diagnostics().getFirst().path()).isEqualTo("/workspace/3");
        assertThat(response.diagnostics().getFirst().code()).isEqualTo("RETRIEVAL_FAILED");
    }

    @Test
    void strictModePropagatesWorkspaceRetrievalFailure() {
        addWorkspace(3L, 7L, "Gamma");
        retrievalProvider.failingWorkspaceIds.add(3L);

        assertThatThrownBy(() -> service.retrieve(command(
                "/workspace",
                "alpha",
                UserFilesystemFailureMode.STRICT
        ))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("workspace 3 failed");
    }

    @Test
    void invalidNamespaceAndForeignWorkspaceRemainStrict() {
        assertThatThrownBy(() -> service.retrieve(command(
                "/attachment/1/file.png",
                "alpha",
                UserFilesystemFailureMode.BEST_EFFORT
        ))).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.retrieve(command(
                "/workspace/2/docs/a.md",
                "alpha",
                UserFilesystemFailureMode.BEST_EFFORT
        ))).isInstanceOf(ForbiddenException.class);
    }

    private UserFilesystemRetrieveCommand command(String path, String query, UserFilesystemFailureMode failureMode) {
        UserFilesystemRetrieveCommand command = new UserFilesystemRetrieveCommand();
        command.setPath(path);
        command.setQuery(query);
        command.setFailureMode(failureMode);
        return command;
    }

    private KnowledgeRetrievalService retrievalService(KnowledgeRetrievalProvider provider) {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.setEnabled(true);
        return new KnowledgeRetrievalService(properties, provider);
    }

    private AuthContextProvider authContext(Long userId) {
        AuthSubject subject = new AuthSubject();
        subject.setUserId(userId);
        subject.setDisplayName("User " + userId);
        return () -> Optional.of(subject);
    }

    private void addWorkspace(Long workspaceId, Long ownerUserId, String name) {
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
        content.setMarkdownText("alpha");
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

    private static class CapturingRetrievalProvider implements KnowledgeRetrievalProvider {

        private final List<KnowledgeRetrievalRequest> requests = new ArrayList<>();
        private final Set<Long> failingWorkspaceIds = new LinkedHashSet<>();

        @Override
        public KnowledgeRetrievalResult retrieve(KnowledgeRetrievalRequest request) {
            requests.add(request);
            if (failingWorkspaceIds.contains(request.getWorkspaceId())) {
                throw new IllegalStateException("workspace " + request.getWorkspaceId() + " failed");
            }
            List<KnowledgeIndexedChunk> chunks = new ArrayList<>();
            for (Long documentId : request.getScopeDocumentIds()) {
                KnowledgeIndexedChunk chunk = new KnowledgeIndexedChunk();
                chunk.setWorkspaceId(request.getWorkspaceId());
                chunk.setDocumentId(documentId);
                chunk.setRevisionId(10L);
                chunk.setTitle("Doc " + documentId);
                chunk.setChunkType("BLOCK");
                chunk.setBlockId("block-" + documentId);
                chunk.setBlockType("PARAGRAPH");
                chunk.setChunkIndex(0);
                chunk.setHeadingPath(List.of("Heading"));
                chunk.setContent("alpha content for " + documentId);
                chunk.setScore(documentId.equals(request.getPreferredDocumentId()) ? 2.0D : 1.0D);
                chunks.add(chunk);
            }
            return new KnowledgeRetrievalResult(chunks);
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
