package io.docpilot.workspace.filesystem;

import io.docpilot.filesystem.Filesystem;
import io.docpilot.filesystem.exception.FileNotFoundException;
import io.docpilot.filesystem.model.FileEntry;
import io.docpilot.filesystem.model.FileEntryType;
import io.docpilot.filesystem.model.GrepMatch;
import io.docpilot.filesystem.model.GrepOptions;
import io.docpilot.filesystem.model.GrepResult;
import io.docpilot.filesystem.path.FilesystemPath;
import io.docpilot.filesystem.path.FilesystemPathNames;
import io.docpilot.filesystem.path.GlobMatcher;
import io.docpilot.filesystem.retrieval.FilesystemRetrieval;
import io.docpilot.filesystem.retrieval.FilesystemRetrievalHit;
import io.docpilot.filesystem.retrieval.FilesystemRetrievalOptions;
import io.docpilot.filesystem.retrieval.FilesystemRetrievalRequest;
import io.docpilot.filesystem.retrieval.FilesystemRetrievalResult;
import io.docpilot.workspace.knowledge.KnowledgeRetrievalService;
import io.docpilot.workspace.knowledge.model.KnowledgeIndexedChunk;
import io.docpilot.workspace.knowledge.model.KnowledgeRetrievalRequest;
import io.docpilot.workspace.knowledge.model.KnowledgeRetrievalResult;
import io.docpilot.workspace.enums.WorkspaceResourceType;
import io.docpilot.workspace.model.entity.Workspace;
import io.docpilot.workspace.model.entity.WorkspaceDocument;
import io.docpilot.workspace.model.entity.WorkspaceNode;
import io.docpilot.workspace.repository.WorkspaceDocumentRepository;
import io.docpilot.workspace.repository.WorkspaceNodeRepository;
import io.docpilot.workspace.repository.WorkspaceRepository;
import io.docpilot.workspace.search.LinearWorkspaceSearchService;
import io.docpilot.workspace.search.WorkspaceSearchDocument;
import io.docpilot.workspace.search.WorkspaceSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Read-only filesystem projection of one workspace tree.
 *
 * <p>Folders become directories. Document resource nodes become Markdown files
 * backed by the current WorkspaceDocument snapshot.</p>
 */
public class WorkspaceFilesystem implements Filesystem, FilesystemRetrieval {

    /**
     * Logger for workspace filesystem operations.
     */
    private static final Logger log = LoggerFactory.getLogger(WorkspaceFilesystem.class);

    /**
     * Workspace id projected by this filesystem instance.
     */
    private final Long workspaceId;

    /**
     * Workspace aggregate repository.
     */
    private final WorkspaceRepository workspaceRepository;

    /**
     * Workspace tree node repository.
     */
    private final WorkspaceNodeRepository nodeRepository;

    /**
     * Workspace document content repository.
     */
    private final WorkspaceDocumentRepository documentRepository;

    /**
     * Search implementation used after path scoping resolves candidate documents.
     */
    private final WorkspaceSearchService searchService;

    /**
     * Optional semantic retrieval service used by the retrieval capability.
     */
    private final KnowledgeRetrievalService retrievalService;

    /**
     * Creates a workspace filesystem with the default linear search implementation.
     */
    public WorkspaceFilesystem(Long workspaceId,
                               WorkspaceRepository workspaceRepository,
                               WorkspaceNodeRepository nodeRepository,
                               WorkspaceDocumentRepository documentRepository) {
        this(workspaceId, workspaceRepository, nodeRepository, documentRepository, new LinearWorkspaceSearchService(), null);
    }

    /**
     * Creates a workspace filesystem with semantic retrieval enabled.
     */
    public WorkspaceFilesystem(Long workspaceId,
                               WorkspaceRepository workspaceRepository,
                               WorkspaceNodeRepository nodeRepository,
                               WorkspaceDocumentRepository documentRepository,
                               KnowledgeRetrievalService retrievalService) {
        this(workspaceId, workspaceRepository, nodeRepository, documentRepository, new LinearWorkspaceSearchService(), retrievalService);
    }

    /**
     * Creates a workspace filesystem with an injectable search implementation.
     */
    public WorkspaceFilesystem(Long workspaceId,
                               WorkspaceRepository workspaceRepository,
                               WorkspaceNodeRepository nodeRepository,
                               WorkspaceDocumentRepository documentRepository,
                               WorkspaceSearchService searchService) {
        this(workspaceId, workspaceRepository, nodeRepository, documentRepository, searchService, null);
    }

    /**
     * Creates a workspace filesystem with injectable search and retrieval implementations.
     */
    public WorkspaceFilesystem(Long workspaceId,
                               WorkspaceRepository workspaceRepository,
                               WorkspaceNodeRepository nodeRepository,
                               WorkspaceDocumentRepository documentRepository,
                               WorkspaceSearchService searchService,
                               KnowledgeRetrievalService retrievalService) {
        if (workspaceId == null || workspaceId <= 0) {
            throw new IllegalArgumentException("workspaceId is required");
        }

        this.workspaceId = workspaceId;
        this.workspaceRepository = workspaceRepository;
        this.nodeRepository = nodeRepository;
        this.documentRepository = documentRepository;
        this.searchService = Objects.requireNonNull(searchService, "searchService");
        this.retrievalService = retrievalService;
    }

    @Override
    public List<FileEntry> list(String path) {
        log.debug("workspace filesystem list start workspaceId={} path={}", workspaceId, path);
        WorkspaceNode node = resolveNode(path);

        if (!node.isFolder()) {
            List<FileEntry> entries = List.of(toEntry(pathOf(node), node));
            log.debug("workspace filesystem list done workspaceId={} path={} nodeId={} folder=false entries={}",
                    workspaceId, path, node.getId(), entries.size());
            return entries;
        }

        List<FileEntry> entries = nodeRepository.findActiveByWorkspaceId(workspaceId).stream()
                .filter(child -> node.getId().equals(child.getParentNodeId()))
                .sorted(Comparator.comparing(WorkspaceNode::getName))
                .map(child -> toEntry(pathOf(child), child))
                .toList();
        log.debug("workspace filesystem list done workspaceId={} path={} nodeId={} folder=true entries={}",
                workspaceId, path, node.getId(), entries.size());
        return entries;
    }

    @Override
    public byte[] read(String path) {
        log.debug("workspace filesystem read start workspaceId={} path={}", workspaceId, path);
        WorkspaceNode node = resolveNode(path);

        if (!node.isDocumentResource()) {
            throw new FileNotFoundException("Workspace path is not a document: " + path);
        }

        byte[] content = markdown(node).getBytes(StandardCharsets.UTF_8);
        log.debug("workspace filesystem read done workspaceId={} path={} nodeId={} documentId={} bytes={}",
                workspaceId, path, node.getId(), node.getDocumentId(), content.length);
        return content;
    }

    @Override
    public boolean exists(String path) {
        log.debug("workspace filesystem exists start workspaceId={} path={}", workspaceId, path);
        try {
            stat(path);
            log.debug("workspace filesystem exists done workspaceId={} path={} exists=true", workspaceId, path);
            return true;
        } catch (FileNotFoundException exception) {
            log.debug("workspace filesystem exists done workspaceId={} path={} exists=false", workspaceId, path);
            return false;
        }
    }

    @Override
    public FileEntry stat(String path) {
        log.debug("workspace filesystem stat start workspaceId={} path={}", workspaceId, path);
        WorkspaceNode node = resolveNode(path);
        FileEntry entry = toEntry(pathOf(node), node);
        log.debug("workspace filesystem stat done workspaceId={} path={} nodeId={} type={} size={}",
                workspaceId, path, node.getId(), entry.type(), entry.size());
        return entry;
    }

    @Override
    public List<FileEntry> glob(String pathPattern) {
        String normalizedPattern = FilesystemPath.normalizeGlobPattern(pathPattern);
        log.debug("workspace filesystem glob start workspaceId={} pattern={} normalizedPattern={}",
                workspaceId, pathPattern, normalizedPattern);

        List<FileEntry> entries = files().stream()
                .filter(entry -> GlobMatcher.matches(normalizedPattern, entry.path()))
                .toList();
        log.debug("workspace filesystem glob done workspaceId={} pattern={} normalizedPattern={} entries={}",
                workspaceId, pathPattern, normalizedPattern, entries.size());
        return entries;
    }

    public List<FileEntry> files() {
        log.debug("workspace filesystem files start workspaceId={}", workspaceId);
        List<FileEntry> entries = workspacePaths().entrySet().stream()
                .filter(entry -> entry.getValue().isDocumentResource())
                .map(entry -> toEntry(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(FileEntry::path))
                .toList();
        log.debug("workspace filesystem files done workspaceId={} entries={}", workspaceId, entries.size());
        return entries;
    }

    @Override
    public List<GrepMatch> grep(String path, String text) {
        return grep(path, text, GrepOptions.unlimited()).matches();
    }

    @Override
    public GrepResult grep(String path, String text, GrepOptions options) {
        GrepOptions grepOptions = GrepOptions.effective(options);
        String normalizedPath = FilesystemPath.normalizeVirtualPath(path);
        log.debug("workspace filesystem grep start workspaceId={} path={} normalizedPath={} textLength={} maxFiles={} maxMatches={}",
                workspaceId, path, normalizedPath, text == null ? 0 : text.length(),
                grepOptions.maxFiles(), grepOptions.maxMatches());
        Map<String, WorkspaceNode> paths = workspacePaths();
        WorkspaceNode root = resolveNode(normalizedPath);
        // A workspace document resource is the file-equivalent unit for grep budgeting.
        List<Map.Entry<String, WorkspaceNode>> candidates = paths.entrySet().stream()
                .filter(entry -> entry.getValue().isDocumentResource())
                .filter(entry -> root.isDocumentResource()
                        ? entry.getKey().equals(normalizedPath)
                        : FilesystemPath.isSameOrDescendant(normalizedPath, entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .toList();
        // The filesystem layer owns path scoping; the search service owns text scanning and result budgets.
        List<WorkspaceSearchDocument> documents = candidates.stream()
                .map(entry -> new WorkspaceSearchDocument(entry.getKey(), markdown(entry.getValue())))
                .toList();
        GrepResult result = searchService.grep(documents, text, grepOptions);
        log.debug("workspace filesystem grep done workspaceId={} path={} normalizedPath={} candidates={} matches={} truncated={} reason={} searchedFiles={}",
                workspaceId, path, normalizedPath, candidates.size(), result.matches().size(), result.truncated(),
                result.truncationReason(), result.searchedFiles());
        return result;
    }

    @Override
    public FilesystemRetrievalResult retrieve(FilesystemRetrievalRequest request) {
        if (retrievalService == null) {
            throw new io.docpilot.filesystem.exception.UnsupportedFilesystemOperationException(
                    "Workspace filesystem retrieval is not configured"
            );
        }

        FilesystemRetrievalRequest retrievalRequest = request == null
                ? new FilesystemRetrievalRequest(FilesystemPathNames.ROOT, "", FilesystemRetrievalOptions.defaults())
                : request;
        FilesystemRetrievalOptions options = retrievalRequest.options();
        // Empty queries and zero-hit requests should not call the knowledge provider at all.
        if (options.topK() == 0 || retrievalRequest.query().isBlank()) {
            return FilesystemRetrievalResult.complete(List.of());
        }

        String normalizedPath = FilesystemPath.normalizeVirtualPath(retrievalRequest.path());
        Workspace workspace = requireWorkspace();
        Map<String, WorkspaceNode> paths = workspacePaths();
        WorkspaceNode root = resolveNode(normalizedPath);
        // Files scope retrieval to exactly that document; folders scope retrieval to descendant documents.
        List<Map.Entry<String, WorkspaceNode>> candidates = paths.entrySet().stream()
                .filter(entry -> entry.getValue().isDocumentResource())
                .filter(entry -> root.isDocumentResource()
                        ? entry.getKey().equals(normalizedPath)
                        : FilesystemPath.isSameOrDescendant(normalizedPath, entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .toList();
        if (candidates.isEmpty()) {
            return FilesystemRetrievalResult.complete(List.of());
        }

        Map<Long, String> pathByDocumentId = new LinkedHashMap<>();
        for (Map.Entry<String, WorkspaceNode> candidate : candidates) {
            // Multiple nodes should not normally reference one document, but keep the first visible path stable.
            pathByDocumentId.putIfAbsent(candidate.getValue().getDocumentId(), candidate.getKey());
        }

        KnowledgeRetrievalRequest knowledgeRequest = new KnowledgeRetrievalRequest();
        knowledgeRequest.setWorkspaceId(workspaceId);
        knowledgeRequest.setOwnerUserId(workspace.getOwnerUserId());
        knowledgeRequest.setPath(normalizedPath);
        knowledgeRequest.setQueryText(retrievalRequest.query());
        knowledgeRequest.setScopeDocumentIds(new ArrayList<>(pathByDocumentId.keySet()));
        // A file path is a ranking hint for that document, not an authorization or scope substitute.
        knowledgeRequest.setPreferredDocumentId(root.isDocumentResource() ? root.getDocumentId() : null);
        knowledgeRequest.setLimit(options.topK());

        KnowledgeRetrievalResult knowledgeResult = retrievalService.retrieve(knowledgeRequest);
        List<FilesystemRetrievalHit> hits = knowledgeResult.chunks().stream()
                // Keep the filesystem layer authoritative for path scope even if a provider returns extra hits.
                .filter(chunk -> pathByDocumentId.containsKey(chunk.getDocumentId()))
                .map(chunk -> toRetrievalHit(chunk, pathByDocumentId.get(chunk.getDocumentId()), options))
                .limit(options.topK())
                .toList();
        return FilesystemRetrievalResult.complete(hits);
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

    private FilesystemRetrievalHit toRetrievalHit(KnowledgeIndexedChunk chunk,
                                                  String path,
                                                  FilesystemRetrievalOptions options) {
        Map<String, String> metadata = new LinkedHashMap<>();
        putMetadata(metadata, "workspaceId", chunk.getWorkspaceId());
        putMetadata(metadata, "documentId", chunk.getDocumentId());
        putMetadata(metadata, "revisionId", chunk.getRevisionId());
        putMetadata(metadata, "chunkType", chunk.getChunkType());
        putMetadata(metadata, "blockId", chunk.getBlockId());
        putMetadata(metadata, "blockType", chunk.getBlockType());
        putMetadata(metadata, "chunkIndex", chunk.getChunkIndex());
        return new FilesystemRetrievalHit(
                path,
                chunk.getTitle(),
                truncate(chunk.getContent(), options.maxCharsPerHit()),
                chunk.getScore(),
                chunk.getHeadingPath(),
                metadata
        );
    }

    private void putMetadata(Map<String, String> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, String.valueOf(value));
        }
    }

    private String truncate(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        if (maxChars <= 0 || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars);
    }

    private Instant updatedAt(WorkspaceNode node) {
        return node.getUpdateTime() == null ? null : node.getUpdateTime().atZone(ZoneId.systemDefault()).toInstant();
    }

}
