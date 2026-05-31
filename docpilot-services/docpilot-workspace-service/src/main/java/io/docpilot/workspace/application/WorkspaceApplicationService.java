package io.docpilot.workspace.application;

import io.docpilot.common.auth.AuthContextProvider;
import io.docpilot.common.auth.AuthSubject;
import io.docpilot.common.exception.ConflictException;
import io.docpilot.common.exception.ForbiddenException;
import io.docpilot.common.exception.NotFoundException;
import io.docpilot.common.exception.UnauthorizedException;
import io.docpilot.common.id.SnowflakeIdGenerator;
import io.docpilot.workspace.model.request.MoveWorkspaceNodeCommand;
import io.docpilot.workspace.model.request.CreateWorkspaceCommand;
import io.docpilot.workspace.model.request.RenameWorkspaceCommand;
import io.docpilot.workspace.model.request.RenameWorkspaceNodeCommand;
import io.docpilot.workspace.model.entity.Workspace;
import io.docpilot.workspace.model.entity.WorkspaceNode;
import io.docpilot.workspace.enums.WorkspaceNodeType;
import io.docpilot.workspace.enums.WorkspaceType;
import io.docpilot.workspace.model.request.CreateFolderCommand;
import io.docpilot.workspace.model.response.WorkspaceListResponse;
import io.docpilot.workspace.model.response.WorkspaceNodeResponse;
import io.docpilot.workspace.model.response.WorkspaceResponse;
import io.docpilot.workspace.model.response.WorkspaceTreeResponse;
import io.docpilot.workspace.processing.WorkspaceIdCodec;
import io.docpilot.workspace.processing.WorkspaceNodeName;
import io.docpilot.workspace.repository.WorkspaceDocumentRepository;
import io.docpilot.workspace.repository.WorkspaceNodeRepository;
import io.docpilot.workspace.repository.WorkspaceRepository;
import lombok.Setter;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Setter
public class WorkspaceApplicationService {

    private WorkspaceRepository workspaceRepository;
    private WorkspaceNodeRepository nodeRepository;
    private WorkspaceDocumentRepository documentRepository;
    private AuthContextProvider authContextProvider;
    private SnowflakeIdGenerator idGenerator;
    private WorkspaceIdCodec idCodec;
    private WorkspaceNodeName workspaceNodeName;
    private WorkspaceTransactionRunner transactionRunner;
    private Clock clock = Clock.systemDefaultZone();

    public WorkspaceResponse ensureDefaultWorkspace() {
        AuthSubject subject = requireSubject();
        Workspace workspace = transactionRunner.run(() -> workspaceRepository
                .findActivePersonalByOwnerUserId(subject.getUserId())
                .orElseGet(() -> createDefaultWorkspace(subject)));
        return toResponse(workspace);
    }

    public WorkspaceListResponse listMyWorkspaces() {
        AuthSubject subject = requireSubject();
        return new WorkspaceListResponse(workspaceRepository.findActiveByOwnerUserId(subject.getUserId())
                .stream()
                .map(this::toResponse)
                .toList());
    }

    public WorkspaceResponse createWorkspace(CreateWorkspaceCommand command) {
        AuthSubject subject = requireSubject();
        Workspace workspace = transactionRunner.run(() -> createWorkspace(subject, normalizeWorkspaceName(command.getName()), WorkspaceType.CUSTOM));
        return toResponse(workspace);
    }

    public WorkspaceResponse renameWorkspace(RenameWorkspaceCommand command) {
        AuthSubject subject = requireSubject();
        Workspace savedWorkspace = transactionRunner.run(() -> {
            Workspace workspace = requireOwnedWorkspace(command.getWorkspaceId(), subject);
            String name = normalizeWorkspaceName(command.getName());
            if (!Objects.equals(workspace.getName(), name)) {
                requireWorkspaceNameAvailable(subject.getUserId(), name);
            }

            workspace.setName(name);
            workspace.markUpdated();

            WorkspaceNode root = requireActiveNode(workspace.getRootNodeId());
            requireNodeInWorkspace(root, workspace.getId());
            root.setName(name);
            root.markUpdated();
            nodeRepository.save(root);
            return workspaceRepository.save(workspace);
        });
        return toResponse(savedWorkspace);
    }

    public void deleteWorkspace(Long workspaceId) {
        AuthSubject subject = requireSubject();
        transactionRunner.run(() -> {
            Workspace workspace = requireOwnedWorkspace(workspaceId, subject);
            if (WorkspaceType.PERSONAL == workspace.getType()) {
                throw new IllegalArgumentException("Default workspace cannot be deleted");
            }

            workspace.setIsDeleted(true);
            workspace.markUpdated();

            List<WorkspaceNode> nodes = nodeRepository.findActiveByWorkspaceId(workspace.getId());
            nodes.forEach(node -> {
                node.setIsDeleted(true);
                node.markUpdated();
                if (node.isDocumentResource()) {
                    softDeleteDocument(node.getDocumentId());
                }
            });
            nodeRepository.saveAll(nodes);
            workspaceRepository.save(workspace);
            return null;
        });
    }

    public WorkspaceTreeResponse getTree(Long workspaceId) {
        Workspace workspace = requireOwnedWorkspace(workspaceId);
        return new WorkspaceTreeResponse(
                toResponse(workspace),
                nodeRepository.findActiveByWorkspaceId(workspaceId).stream()
                        .map(this::toResponse)
                        .toList()
        );
    }

    public WorkspaceNodeResponse createFolder(CreateFolderCommand command) {
        AuthSubject subject = requireSubject();
        WorkspaceNode savedNode = transactionRunner.run(() -> {
            Workspace workspace = requireOwnedWorkspace(command.getWorkspaceId(), subject);
            WorkspaceNode parent = requireActiveNode(command.getParentNodeId() == null
                    ? workspace.getRootNodeId()
                    : command.getParentNodeId());
            requireNodeInWorkspace(parent, workspace.getId());
            requireFolder(parent);

            String nodeName = workspaceNodeName.normalizeName(command.getName());
            requireNameAvailable(workspace.getId(), parent.getId(), nodeName);

            WorkspaceNode node = new WorkspaceNode();
            node.setId(idGenerator.nextId());
            node.setWorkspaceId(workspace.getId());
            node.setParentNodeId(parent.getId());
            node.setAncestors(childAncestors(parent));
            node.setNodeType(WorkspaceNodeType.FOLDER);
            node.setName(nodeName);
            node.markCreated();
            return nodeRepository.save(node);
        });
        return toResponse(savedNode);
    }

    public WorkspaceNodeResponse renameNode(RenameWorkspaceNodeCommand command) {
        AuthSubject subject = requireSubject();
        WorkspaceNode savedNode = transactionRunner.run(() -> {
            WorkspaceNode node = requireActiveNode(command.getNodeId());
            Workspace workspace = requireOwnedWorkspace(node.getWorkspaceId(), subject);
            if (Objects.equals(node.getId(), workspace.getRootNodeId())) {
                throw new IllegalArgumentException("Root node cannot be renamed");
            }

            WorkspaceNode parent = requireActiveNode(node.getParentNodeId());
            String nodeName = workspaceNodeName.normalizeName(command.getName());
            if (!Objects.equals(node.getName(), nodeName)) {
                requireNameAvailable(workspace.getId(), parent.getId(), nodeName);
            }

            node.setName(nodeName);
            node.markUpdated();
            return nodeRepository.save(node);
        });
        return toResponse(savedNode);
    }

    public WorkspaceNodeResponse moveNode(MoveWorkspaceNodeCommand command) {
        AuthSubject subject = requireSubject();
        WorkspaceNode savedNode = transactionRunner.run(() -> {
            WorkspaceNode node = requireActiveNode(command.getNodeId());
            Workspace workspace = requireOwnedWorkspace(node.getWorkspaceId(), subject);
            if (Objects.equals(node.getId(), workspace.getRootNodeId())) {
                throw new IllegalArgumentException("Root node cannot be moved");
            }

            WorkspaceNode newParent = requireActiveNode(command.getParentNodeId());
            requireNodeInWorkspace(newParent, workspace.getId());
            requireFolder(newParent);
            if (Objects.equals(newParent.getId(), node.getId()) || newParent.getAncestors().contains(node.getId())) {
                throw new IllegalArgumentException("Node cannot be moved under itself or its descendant");
            }
            if (!Objects.equals(newParent.getId(), node.getParentNodeId())) {
                requireNameAvailable(workspace.getId(), newParent.getId(), node.getName());
            }

            node.setParentNodeId(newParent.getId());
            node.setAncestors(childAncestors(newParent));
            node.markUpdated();
            WorkspaceNode saved = nodeRepository.save(node);
            updateDescendantLocations(saved);
            return saved;
        });
        return toResponse(savedNode);
    }

    public void deleteNode(Long nodeId) {
        AuthSubject subject = requireSubject();
        transactionRunner.run(() -> {
            WorkspaceNode node = requireActiveNode(nodeId);
            requireOwnedWorkspace(node.getWorkspaceId(), subject);

            List<WorkspaceNode> nodes = new ArrayList<>();
            nodes.add(node);
            nodes.addAll(nodeRepository.findActiveByWorkspaceIdAndAncestor(node.getWorkspaceId(), node.getId()));
            nodes.forEach(candidate -> {
                candidate.setIsDeleted(true);
                candidate.markUpdated();
                if (candidate.isDocumentResource()) {
                    softDeleteDocument(candidate.getDocumentId());
                }
            });
            nodeRepository.saveAll(nodes);
            return null;
        });
    }

    Workspace requireOwnedWorkspace(Long workspaceId) {
        return requireOwnedWorkspace(workspaceId, requireSubject());
    }

    WorkspaceNode requireActiveNode(Long nodeId) {
        if (nodeId == null || nodeId <= 0) {
            throw new IllegalArgumentException("nodeId is required");
        }
        WorkspaceNode node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new NotFoundException("Workspace node not found: " + nodeId));
        if (Boolean.TRUE.equals(node.getIsDeleted())) {
            throw new NotFoundException("Workspace node not found: " + nodeId);
        }
        return node;
    }

    private Workspace createDefaultWorkspace(AuthSubject subject) {
        String name = subject.getDisplayName() == null || subject.getDisplayName().isBlank()
                ? "Personal Workspace"
                : subject.getDisplayName().strip() + " Workspace";
        return createWorkspace(subject, name, WorkspaceType.PERSONAL);
    }

    private Workspace createWorkspace(AuthSubject subject, String name, WorkspaceType type) {
        requireWorkspaceNameAvailable(subject.getUserId(), name);
        long workspaceId = idGenerator.nextId();
        long rootNodeId = idGenerator.nextId();

        Workspace workspace = new Workspace();
        workspace.setId(workspaceId);
        workspace.setName(name);
        workspace.setType(type);
        workspace.setOwnerUserId(subject.getUserId());
        workspace.setRootNodeId(rootNodeId);
        workspace.markCreated();

        WorkspaceNode root = new WorkspaceNode();
        root.setId(rootNodeId);
        root.setWorkspaceId(workspaceId);
        root.setParentNodeId(WorkspaceNode.ROOT_PARENT_NODE_ID);
        root.setAncestors(List.of());
        root.setNodeType(WorkspaceNodeType.FOLDER);
        root.setName(name);
        root.markCreated();

        Workspace saved = workspaceRepository.save(workspace);
        nodeRepository.save(root);
        return saved;
    }

    private String normalizeWorkspaceName(String name) {
        return workspaceNodeName.normalizeName(name);
    }

    private void requireWorkspaceNameAvailable(Long ownerUserId, String name) {
        workspaceRepository.findActiveByOwnerUserId(ownerUserId).stream()
                .filter(workspace -> name.equals(workspace.getName()))
                .findFirst()
                .ifPresent(existing -> {
                    throw new ConflictException("A workspace with the same name already exists");
                });
    }

    private Workspace requireOwnedWorkspace(Long workspaceId, AuthSubject subject) {
        if (workspaceId == null || workspaceId <= 0) {
            throw new IllegalArgumentException("workspaceId is required");
        }
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new NotFoundException("Workspace not found: " + workspaceId));
        if (Boolean.TRUE.equals(workspace.getIsDeleted())) {
            throw new NotFoundException("Workspace not found: " + workspaceId);
        }
        if (!Objects.equals(workspace.getOwnerUserId(), subject.getUserId())) {
            throw new ForbiddenException("Workspace access denied");
        }
        return workspace;
    }

    private AuthSubject requireSubject() {
        return authContextProvider.currentSubject()
                .orElseThrow(() -> new UnauthorizedException("Authentication is required"));
    }

    private void requireNodeInWorkspace(WorkspaceNode node, Long workspaceId) {
        if (!Objects.equals(node.getWorkspaceId(), workspaceId)) {
            throw new ForbiddenException("Workspace node access denied");
        }
    }

    private void requireFolder(WorkspaceNode node) {
        if (!node.isFolder()) {
            throw new IllegalArgumentException("Parent node must be a folder");
        }
    }

    private void requireNameAvailable(Long workspaceId, Long parentNodeId, String name) {
        nodeRepository.findActiveByWorkspaceIdAndParentNodeIdAndName(
                workspaceId, parentNodeId, name).ifPresent(existing -> {
            throw new ConflictException("A node with the same name already exists");
        });
    }

    private List<Long> childAncestors(WorkspaceNode parent) {
        List<Long> ancestors = new ArrayList<>(parent.getAncestors());
        ancestors.add(parent.getId());
        return ancestors;
    }

    private void updateDescendantLocations(WorkspaceNode node) {
        List<WorkspaceNode> descendants = nodeRepository.findActiveByWorkspaceIdAndAncestor(node.getWorkspaceId(), node.getId());
        List<Long> movedPrefix = childAncestors(node);
        descendants.forEach(descendant -> {
            int movedNodeIndex = descendant.getAncestors().indexOf(node.getId());
            List<Long> suffix = movedNodeIndex < 0
                    ? List.of()
                    : descendant.getAncestors().subList(movedNodeIndex + 1, descendant.getAncestors().size());
            List<Long> newAncestors = new ArrayList<>(movedPrefix);
            newAncestors.addAll(suffix);
            descendant.setAncestors(newAncestors);
            descendant.markUpdated();
        });
        nodeRepository.saveAll(descendants);
    }

    private void softDeleteDocument(Long documentId) {
        documentRepository.findById(documentId).ifPresent(document -> {
            document.setIsDeleted(true);
            document.markUpdated();
            documentRepository.save(document);
        });
    }

    private WorkspaceResponse toResponse(Workspace workspace) {
        return new WorkspaceResponse(
                idCodec.format(workspace.getId()),
                workspace.getName(),
                workspace.getType(),
                idCodec.format(workspace.getOwnerUserId()),
                idCodec.format(workspace.getRootNodeId()),
                workspace.getSettings(),
                format(workspace.getCreateTime()),
                format(workspace.getUpdateTime())
        );
    }

    private WorkspaceNodeResponse toResponse(WorkspaceNode node) {
        return new WorkspaceNodeResponse(
                idCodec.format(node.getId()),
                idCodec.format(node.getWorkspaceId()),
                node.getParentNodeId() == null || node.getParentNodeId() == 0 ? null : idCodec.format(node.getParentNodeId()),
                node.getAncestors().stream().map(idCodec::format).toList(),
                node.getNodeType(),
                node.getResourceType(),
                idCodec.format(node.getDocumentId()),
                node.getStorage(),
                node.getName(),
                node.getMimeType(),
                idCodec.format(node.getSize()),
                node.getChecksum(),
                node.getMetadata(),
                format(node.getCreateTime()),
                format(node.getUpdateTime())
        );
    }

    private String format(LocalDateTime instant) {
        return instant == null ? null : instant.toString();
    }

}


