package io.docpilot.document.application;

import io.docpilot.document.auth.AuthContextProvider;
import io.docpilot.document.auth.AuthSubject;
import io.docpilot.document.auth.DefaultDocumentAccessAuthorizer;
import io.docpilot.document.model.CreateDocumentCommand;
import io.docpilot.document.model.CreateWorkspaceCommand;
import io.docpilot.document.model.CreateWorkspaceNodeCommand;
import io.docpilot.document.model.DocPilotDocument;
import io.docpilot.document.model.DocumentRole;
import io.docpilot.document.model.DocumentShare;
import io.docpilot.document.model.ShareDocumentCommand;
import io.docpilot.document.model.UpdateDocumentContentCommand;
import io.docpilot.document.model.Workspace;
import io.docpilot.document.model.WorkspaceNode;
import io.docpilot.document.model.WorkspaceNodeType;
import io.docpilot.document.repository.DocumentRepository;
import io.docpilot.document.repository.DocumentShareRepository;
import io.docpilot.document.repository.WorkspaceNodeRepository;
import io.docpilot.document.repository.WorkspaceRepository;
import io.docpilot.document.user.UserProfileProvider;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentArchitectureTest {

    @Test
    void workspaceCanBeCreatedAsUserResourceContainer() {
        InMemoryWorkspaceRepository workspaceRepository = new InMemoryWorkspaceRepository();
        InMemoryWorkspaceNodeRepository nodeRepository = new InMemoryWorkspaceNodeRepository();
        WorkspaceManager manager = workspaceManager(workspaceRepository, nodeRepository, subject("u1", "Alice"));

        CreateWorkspaceCommand command = new CreateWorkspaceCommand();
        command.setName("  Personal  ");

        Workspace workspace = manager.createWorkspace(command);

        assertThat(workspace.getWorkspaceId()).matches("[0-9a-f]{32}");
        assertThat(workspace.getOwnerUserId()).isEqualTo("u1");
        assertThat(workspace.getName()).isEqualTo("Personal");
        assertThat(nodeRepository.findById(workspace.getRootNodeId()))
                .hasValueSatisfying(root -> {
                    assertThat(root.getType()).isEqualTo(WorkspaceNodeType.FOLDER);
                    assertThat(root.getWorkspaceId()).isEqualTo(workspace.getWorkspaceId());
                });
    }

    @Test
    void documentCanBeLinkedByWorkspaceNode() {
        InMemoryDocumentRepository documentRepository = new InMemoryDocumentRepository();
        InMemoryWorkspaceRepository workspaceRepository = new InMemoryWorkspaceRepository();
        InMemoryWorkspaceNodeRepository nodeRepository = new InMemoryWorkspaceNodeRepository();
        AuthSubject subject = subject("u1", "Alice");
        DocumentManager documentManager = documentManager(documentRepository, subject);
        WorkspaceManager workspaceManager = workspaceManager(workspaceRepository, nodeRepository, subject);

        DocPilotDocument document = documentManager.createDocument(new CreateDocumentCommand());
        Workspace workspace = workspaceManager.createWorkspace(new CreateWorkspaceCommand());
        CreateWorkspaceNodeCommand command = new CreateWorkspaceNodeCommand();
        command.setWorkspaceId(workspace.getWorkspaceId());
        command.setType(WorkspaceNodeType.DOCUMENT);
        command.setName("Spec");
        command.setDocumentId(document.getDocumentId());

        WorkspaceNode node = workspaceManager.createNode(command);

        assertThat(node.getType()).isEqualTo(WorkspaceNodeType.DOCUMENT);
        assertThat(node.getParentNodeId()).isEqualTo(workspace.getRootNodeId());
        assertThat(node.getDocumentId()).isEqualTo(document.getDocumentId());
        assertThat(workspaceManager.listChildren(workspace.getWorkspaceId(), null)).containsExactly(node);
    }

    @Test
    void shareCanExpressTargetUserAndRole() {
        InMemoryDocumentRepository documentRepository = new InMemoryDocumentRepository();
        InMemoryDocumentShareRepository shareRepository = new InMemoryDocumentShareRepository();
        AuthSubject owner = subject("u1", "Alice");
        DocumentManager documentManager = documentManager(documentRepository, owner);
        DocumentShareManager shareManager = new DocumentShareManager(documentRepository, shareRepository, () -> Optional.of(owner));
        DocPilotDocument document = documentManager.createDocument(new CreateDocumentCommand());

        ShareDocumentCommand command = new ShareDocumentCommand();
        command.setDocumentId(document.getDocumentId());
        command.setTargetUserId("u2");
        command.setRole(DocumentRole.EDITOR);

        DocumentShare share = shareManager.shareDocument(command);

        assertThat(share.getDocumentId()).isEqualTo(document.getDocumentId());
        assertThat(share.getOwnerUserId()).isEqualTo("u1");
        assertThat(share.getTargetUserId()).isEqualTo("u2");
        assertThat(share.getRole()).isEqualTo(DocumentRole.EDITOR);
    }

    @Test
    void documentManagerStillUpdatesContentAndParsesBlock() {
        InMemoryDocumentRepository repository = new InMemoryDocumentRepository();
        DocumentManager manager = documentManager(repository, subject("u1", "Alice"));
        DocPilotDocument document = manager.createDocument(new CreateDocumentCommand());
        UpdateDocumentContentCommand command = new UpdateDocumentContentCommand();
        command.setExpectedVersion(document.getVersion());
        command.setMarkdown("# Updated\n\nContent");

        DocPilotDocument updated = manager.replaceContent(document.getDocumentId(), command);

        assertThat(updated.getVersion()).isEqualTo(2);
        assertThat(updated.getMarkdown()).isEqualTo("# Updated\n\nContent");
        assertThat(updated.getBlockDocument().getBlocks()).hasSize(2);
    }

    private DocumentManager documentManager(InMemoryDocumentRepository repository, AuthSubject subject) {
        AuthContextProvider authContextProvider = () -> Optional.ofNullable(subject);
        UserProfileProvider userProfileProvider = userId -> Optional.empty();
        return new DocumentManager(repository, authContextProvider, new DefaultDocumentAccessAuthorizer(), userProfileProvider);
    }

    private WorkspaceManager workspaceManager(InMemoryWorkspaceRepository workspaceRepository,
                                              InMemoryWorkspaceNodeRepository nodeRepository,
                                              AuthSubject subject) {
        return new WorkspaceManager(workspaceRepository, nodeRepository, () -> Optional.ofNullable(subject));
    }

    private AuthSubject subject(String userId, String displayName) {
        AuthSubject subject = new AuthSubject();
        subject.setUserId(userId);
        subject.setDisplayName(displayName);
        return subject;
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

    private static final class InMemoryWorkspaceRepository implements WorkspaceRepository {

        private final Map<String, Workspace> workspaces = new HashMap<>();

        @Override
        public Workspace save(Workspace workspace) {
            workspaces.put(workspace.getWorkspaceId(), workspace);
            return workspace;
        }

        @Override
        public Optional<Workspace> findById(String workspaceId) {
            return Optional.ofNullable(workspaces.get(workspaceId));
        }

        @Override
        public List<Workspace> findByOwnerUserId(String ownerUserId) {
            return workspaces.values().stream()
                    .filter(workspace -> Objects.equals(workspace.getOwnerUserId(), ownerUserId))
                    .toList();
        }

    }

    private static final class InMemoryWorkspaceNodeRepository implements WorkspaceNodeRepository {

        private final Map<String, WorkspaceNode> nodes = new HashMap<>();

        @Override
        public WorkspaceNode save(WorkspaceNode node) {
            nodes.put(node.getNodeId(), node);
            return node;
        }

        @Override
        public Optional<WorkspaceNode> findById(String nodeId) {
            return Optional.ofNullable(nodes.get(nodeId));
        }

        @Override
        public List<WorkspaceNode> findByWorkspaceIdAndParentNodeId(String workspaceId, String parentNodeId) {
            return nodes.values().stream()
                    .filter(node -> Objects.equals(node.getWorkspaceId(), workspaceId))
                    .filter(node -> Objects.equals(node.getParentNodeId(), parentNodeId))
                    .toList();
        }

    }

    private static final class InMemoryDocumentShareRepository implements DocumentShareRepository {

        private final Map<String, DocumentShare> shares = new HashMap<>();

        @Override
        public DocumentShare save(DocumentShare share) {
            shares.put(share.getShareId(), share);
            return share;
        }

        @Override
        public Optional<DocumentShare> findById(String shareId) {
            return Optional.ofNullable(shares.get(shareId));
        }

        @Override
        public List<DocumentShare> findByTargetUserId(String targetUserId) {
            return shares.values().stream()
                    .filter(share -> Objects.equals(share.getTargetUserId(), targetUserId))
                    .toList();
        }

        @Override
        public Optional<DocumentShare> findByDocumentIdAndTargetUserId(String documentId, String targetUserId) {
            return shares.values().stream()
                    .filter(share -> Objects.equals(share.getDocumentId(), documentId))
                    .filter(share -> Objects.equals(share.getTargetUserId(), targetUserId))
                    .findFirst();
        }

    }

}
