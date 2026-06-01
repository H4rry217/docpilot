package io.docpilot.workspace.filesystem;

import io.docpilot.filesystem.Filesystem;
import io.docpilot.filesystem.exception.FileNotFoundException;
import io.docpilot.filesystem.model.FileEntry;
import io.docpilot.filesystem.model.FileEntryType;
import io.docpilot.filesystem.model.GrepMatch;
import io.docpilot.filesystem.path.FilesystemPath;
import io.docpilot.filesystem.path.FilesystemPathNames;
import io.docpilot.filesystem.path.GlobMatcher;
import io.docpilot.workspace.enums.WorkspaceResourceType;
import io.docpilot.workspace.model.entity.Workspace;
import io.docpilot.workspace.model.entity.WorkspaceDocument;
import io.docpilot.workspace.model.entity.WorkspaceNode;
import io.docpilot.workspace.repository.WorkspaceDocumentRepository;
import io.docpilot.workspace.repository.WorkspaceNodeRepository;
import io.docpilot.workspace.repository.WorkspaceRepository;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only filesystem projection of one workspace tree.
 *
 * <p>Folders become directories. Document resource nodes become Markdown files
 * backed by the current WorkspaceDocument snapshot.</p>
 */
public class WorkspaceFilesystem implements Filesystem {

    private final Long workspaceId;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceNodeRepository nodeRepository;
    private final WorkspaceDocumentRepository documentRepository;

    public WorkspaceFilesystem(Long workspaceId,
                               WorkspaceRepository workspaceRepository,
                               WorkspaceNodeRepository nodeRepository,
                               WorkspaceDocumentRepository documentRepository) {
        if (workspaceId == null || workspaceId <= 0) {
            throw new IllegalArgumentException("workspaceId is required");
        }

        this.workspaceId = workspaceId;
        this.workspaceRepository = workspaceRepository;
        this.nodeRepository = nodeRepository;
        this.documentRepository = documentRepository;
    }

    @Override
    public List<FileEntry> list(String path) {
        WorkspaceNode node = resolveNode(path);

        if (!node.isFolder()) {
            return List.of(toEntry(pathOf(node), node));
        }

        return nodeRepository.findActiveByWorkspaceId(workspaceId).stream()
                .filter(child -> node.getId().equals(child.getParentNodeId()))
                .sorted(Comparator.comparing(WorkspaceNode::getName))
                .map(child -> toEntry(pathOf(child), child))
                .toList();
    }

    @Override
    public byte[] read(String path) {
        WorkspaceNode node = resolveNode(path);

        if (!node.isDocumentResource()) {
            throw new FileNotFoundException("Workspace path is not a document: " + path);
        }

        return markdown(node).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean exists(String path) {
        try {
            stat(path);
            return true;
        } catch (FileNotFoundException exception) {
            return false;
        }
    }

    @Override
    public FileEntry stat(String path) {
        WorkspaceNode node = resolveNode(path);
        return toEntry(pathOf(node), node);
    }

    @Override
    public List<FileEntry> glob(String pathPattern) {
        String normalizedPattern = FilesystemPath.normalizeGlobPattern(pathPattern);

        return files().stream()
                .filter(entry -> GlobMatcher.matches(normalizedPattern, entry.path()))
                .toList();
    }

    public List<FileEntry> files() {
        return workspacePaths().entrySet().stream()
                .filter(entry -> entry.getValue().isDocumentResource())
                .map(entry -> toEntry(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(FileEntry::path))
                .toList();
    }

    @Override
    public List<GrepMatch> grep(String path, String text) {
        String normalizedPath = FilesystemPath.normalizeVirtualPath(path);
        Map<String, WorkspaceNode> paths = workspacePaths();
        WorkspaceNode root = resolveNode(normalizedPath);
        List<GrepMatch> matches = new ArrayList<>();

        paths.entrySet().stream()
                .filter(entry -> entry.getValue().isDocumentResource())
                .filter(entry -> root.isDocumentResource()
                        ? entry.getKey().equals(normalizedPath)
                        : FilesystemPath.isSameOrDescendant(normalizedPath, entry.getKey()))
                .forEach(entry -> grepDocument(entry.getKey(), entry.getValue(), text, matches));
        return matches;
    }

    private void grepDocument(String path, WorkspaceNode node, String text, List<GrepMatch> matches) {
        String[] lines = markdown(node).split("\\R", -1);

        for (int index = 0; index < lines.length; index++) {
            if (lines[index].contains(text)) {
                matches.add(new GrepMatch(path, index + 1L, lines[index]));
            }
        }
    }

    private WorkspaceNode resolveNode(String path) {
        String normalizedPath = FilesystemPath.normalizeVirtualPath(path);
        Workspace workspace = requireWorkspace();
        WorkspaceNode current = requireNode(workspace.getRootNodeId());

        if (FilesystemPathNames.ROOT.equals(normalizedPath)) {
            return current;
        }

        // Walk by node name so the virtual path follows the workspace tree structure.
        for (String segment : FilesystemPath.relativeVirtualPath(FilesystemPathNames.ROOT, normalizedPath)
                .split(FilesystemPathNames.ROOT)) {
            if (!current.isFolder()) {
                throw new FileNotFoundException("Workspace path not found: " + normalizedPath);
            }

            current = nodeRepository.findActiveByWorkspaceIdAndParentNodeIdAndName(workspaceId, current.getId(), segment)
                    .orElseThrow(() -> new FileNotFoundException("Workspace path not found: " + normalizedPath));
        }

        return current;
    }

    private Workspace requireWorkspace() {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new FileNotFoundException("Workspace not found: " + workspaceId));

        if (Boolean.TRUE.equals(workspace.getIsDeleted())) {
            throw new FileNotFoundException("Workspace not found: " + workspaceId);
        }

        return workspace;
    }

    private WorkspaceNode requireNode(Long nodeId) {
        WorkspaceNode node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new FileNotFoundException("Workspace node not found: " + nodeId));

        if (Boolean.TRUE.equals(node.getIsDeleted())) {
            throw new FileNotFoundException("Workspace node not found: " + nodeId);
        }

        return node;
    }

    private Map<String, WorkspaceNode> workspacePaths() {
        Workspace workspace = requireWorkspace();
        Map<Long, WorkspaceNode> byId = new HashMap<>();

        nodeRepository.findActiveByWorkspaceId(workspaceId).forEach(node -> byId.put(node.getId(), node));

        WorkspaceNode root = byId.get(workspace.getRootNodeId());
        if (root == null) {
            root = requireNode(workspace.getRootNodeId());
            byId.put(root.getId(), root);
        }

        Map<String, WorkspaceNode> paths = new HashMap<>();
        paths.put(FilesystemPathNames.ROOT, root);

        for (WorkspaceNode node : byId.values()) {
            if (!node.getId().equals(root.getId())) {
                paths.put(pathOf(node, byId, root), node);
            }
        }

        return paths;
    }

    private String pathOf(WorkspaceNode node) {
        Workspace workspace = requireWorkspace();
        Map<Long, WorkspaceNode> byId = new HashMap<>();

        nodeRepository.findActiveByWorkspaceId(workspaceId).forEach(candidate -> byId.put(candidate.getId(), candidate));

        WorkspaceNode root = byId.getOrDefault(workspace.getRootNodeId(), requireNode(workspace.getRootNodeId()));
        return pathOf(node, byId, root);
    }

    private String pathOf(WorkspaceNode node, Map<Long, WorkspaceNode> byId, WorkspaceNode root) {
        if (node.getId().equals(root.getId())) {
            return FilesystemPathNames.ROOT;
        }

        List<String> segments = new ArrayList<>();

        // Ancestors are stored as ids; resolve them back to names for a user-readable path.
        for (Long ancestorId : node.getAncestors()) {
            WorkspaceNode ancestor = byId.get(ancestorId);

            if (ancestor != null && !ancestor.getId().equals(root.getId())) {
                segments.add(ancestor.getName());
            }
        }

        segments.add(node.getName());

        return FilesystemPathNames.ROOT + String.join(FilesystemPathNames.ROOT, segments);
    }

    private FileEntry toEntry(String path, WorkspaceNode node) {
        Workspace workspace = requireWorkspace();

        return new FileEntry(
                FilesystemPath.normalizeVirtualPath(path),
                node.getId().equals(workspace.getRootNodeId()) ? "" : node.getName(),
                node.isFolder() ? FileEntryType.DIRECTORY : FileEntryType.FILE,
                node.isDocumentResource() ? markdown(node).getBytes(StandardCharsets.UTF_8).length : 0L,
                updatedAt(node)
        );
    }

    private String markdown(WorkspaceNode node) {
        if (node.getResourceType() != WorkspaceResourceType.DOCUMENT || node.getDocumentId() == null) {
            throw new FileNotFoundException("Workspace node is not a document: " + node.getId());
        }

        WorkspaceDocument document = documentRepository.findById(node.getDocumentId())
                .orElseThrow(() -> new FileNotFoundException("Document not found: " + node.getDocumentId()));

        if (Boolean.TRUE.equals(document.getIsDeleted())) {
            throw new FileNotFoundException("Document not found: " + node.getDocumentId());
        }

        if (document.getContent() == null || document.getContent().getMarkdownText() == null) {
            return "";
        }

        return document.getContent().getMarkdownText();
    }

    private Instant updatedAt(WorkspaceNode node) {
        return node.getUpdateTime() == null ? null : node.getUpdateTime().atZone(ZoneId.systemDefault()).toInstant();
    }

}
