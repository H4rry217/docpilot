package io.docpilot.document.application;

import io.docpilot.document.auth.AuthContextProvider;
import io.docpilot.document.auth.AuthSubject;
import io.docpilot.document.exception.DocumentAccessDeniedException;
import io.docpilot.document.exception.WorkspaceNotFoundException;
import io.docpilot.document.model.CreateWorkspaceCommand;
import io.docpilot.document.model.CreateWorkspaceNodeCommand;
import io.docpilot.document.model.Workspace;
import io.docpilot.document.model.WorkspaceNode;
import io.docpilot.document.model.WorkspaceNodeState;
import io.docpilot.document.model.WorkspaceNodeType;
import io.docpilot.document.model.WorkspaceState;
import io.docpilot.document.processing.WorkspaceIdGenerator;
import io.docpilot.document.processing.WorkspaceNodeIdGenerator;
import io.docpilot.document.repository.WorkspaceNodeRepository;
import io.docpilot.document.repository.WorkspaceRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Application boundary for workspace-level operations.
 */
public class WorkspaceManager {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceNodeRepository workspaceNodeRepository;
    private final AuthContextProvider authContextProvider;
    private final WorkspaceIdGenerator workspaceIdGenerator;
    private final WorkspaceNodeIdGenerator workspaceNodeIdGenerator;
    private final Clock clock;
    private final Consumer<Workspace> workspaceCreatedListener;

    public WorkspaceManager(WorkspaceRepository workspaceRepository,
                            WorkspaceNodeRepository workspaceNodeRepository,
                            AuthContextProvider authContextProvider) {
        this(workspaceRepository, workspaceNodeRepository, authContextProvider,
                new WorkspaceIdGenerator(), new WorkspaceNodeIdGenerator(), Clock.systemDefaultZone(), workspace -> {
                });
    }

    public WorkspaceManager(WorkspaceRepository workspaceRepository,
                            WorkspaceNodeRepository workspaceNodeRepository,
                            AuthContextProvider authContextProvider,
                            WorkspaceIdGenerator workspaceIdGenerator,
                            WorkspaceNodeIdGenerator workspaceNodeIdGenerator,
                            Clock clock) {
        this(workspaceRepository, workspaceNodeRepository, authContextProvider,
                workspaceIdGenerator, workspaceNodeIdGenerator, clock, workspace -> {
                });
    }

    public WorkspaceManager(WorkspaceRepository workspaceRepository,
                            WorkspaceNodeRepository workspaceNodeRepository,
                            AuthContextProvider authContextProvider,
                            WorkspaceIdGenerator workspaceIdGenerator,
                            WorkspaceNodeIdGenerator workspaceNodeIdGenerator,
                            Clock clock,
                            Consumer<Workspace> workspaceCreatedListener) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceNodeRepository = workspaceNodeRepository;
        this.authContextProvider = authContextProvider;
        this.workspaceIdGenerator = workspaceIdGenerator;
        this.workspaceNodeIdGenerator = workspaceNodeIdGenerator;
        this.clock = clock;
        this.workspaceCreatedListener = workspaceCreatedListener;
    }

    /**
     * Creates a workspace owned by the current authenticated subject.
     */
    public Workspace createWorkspace(CreateWorkspaceCommand command) {
        AuthSubject subject = requireSubject();
        Instant now = clock.instant();

        Workspace workspace = new Workspace();
        workspace.setWorkspaceId(workspaceIdGenerator.nextId());
        workspace.setOwnerUserId(subject.getUserId());
        workspace.setName(normalizeName(command.getName()));
        workspace.setRootNodeId(workspaceNodeIdGenerator.nextId());
        workspace.setState(WorkspaceState.ACTIVE);
        workspace.setCreateTime(now);
        workspace.setUpdateTime(now);

        Workspace savedWorkspace = workspaceRepository.save(workspace);
        workspaceNodeRepository.save(createRootNode(savedWorkspace, now));
        workspaceCreatedListener.accept(savedWorkspace);
        return savedWorkspace;
    }

    /**
     * Lists workspaces owned by the current authenticated subject.
     */
    public List<Workspace> listMyWorkspaces() {
        AuthSubject subject = requireSubject();
        return workspaceRepository.findByOwnerUserId(subject.getUserId());
    }

    /**
     * Creates a folder or document node inside an owned workspace.
     */
    public WorkspaceNode createNode(CreateWorkspaceNodeCommand command) {
        AuthSubject subject = requireSubject();
        Workspace workspace = requireOwnedWorkspace(command.getWorkspaceId(), subject);
        Instant now = clock.instant();

        WorkspaceNodeType type = command.getType() == null ? WorkspaceNodeType.DOCUMENT : command.getType();
        WorkspaceNode node = new WorkspaceNode();
        node.setNodeId(workspaceNodeIdGenerator.nextId());
        node.setWorkspaceId(workspace.getWorkspaceId());
        node.setParentNodeId(command.getParentNodeId() == null ? workspace.getRootNodeId() : command.getParentNodeId());
        node.setType(type);
        node.setName(normalizeName(command.getName()));
        node.setDocumentId(type == WorkspaceNodeType.DOCUMENT ? command.getDocumentId() : null);
        node.setState(WorkspaceNodeState.ACTIVE);
        node.setCreateTime(now);
        node.setUpdateTime(now);
        return workspaceNodeRepository.save(node);
    }

    /**
     * Lists direct children of a workspace node.
     */
    public List<WorkspaceNode> listChildren(String workspaceId, String parentNodeId) {
        AuthSubject subject = requireSubject();
        Workspace workspace = requireOwnedWorkspace(workspaceId, subject);
        String resolvedParentNodeId = parentNodeId == null ? workspace.getRootNodeId() : parentNodeId;
        return workspaceNodeRepository.findByWorkspaceIdAndParentNodeId(workspace.getWorkspaceId(), resolvedParentNodeId);
    }

    private AuthSubject requireSubject() {
        return authContextProvider.currentSubject()
                .orElseThrow(() -> new DocumentAccessDeniedException("Authentication is required"));
    }

    private Workspace requireOwnedWorkspace(String workspaceId, AuthSubject subject) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new WorkspaceNotFoundException("Workspace not found: " + workspaceId));
        if (!Objects.equals(workspace.getOwnerUserId(), subject.getUserId())) {
            throw new DocumentAccessDeniedException("Workspace access denied");
        }
        return workspace;
    }

    private WorkspaceNode createRootNode(Workspace workspace, Instant now) {
        WorkspaceNode rootNode = new WorkspaceNode();
        rootNode.setNodeId(workspace.getRootNodeId());
        rootNode.setWorkspaceId(workspace.getWorkspaceId());
        rootNode.setType(WorkspaceNodeType.FOLDER);
        rootNode.setName(workspace.getName());
        rootNode.setState(WorkspaceNodeState.ACTIVE);
        rootNode.setCreateTime(now);
        rootNode.setUpdateTime(now);
        return rootNode;
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            return "Untitled Workspace";
        }
        return name.strip();
    }

}
