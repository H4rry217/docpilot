package io.docpilot.workspace.filesystem;

import io.docpilot.common.auth.AuthContextProvider;
import io.docpilot.common.auth.AuthSubject;
import io.docpilot.common.exception.ForbiddenException;
import io.docpilot.common.exception.NotFoundException;
import io.docpilot.common.exception.UnauthorizedException;
import io.docpilot.filesystem.exception.InvalidPathException;
import io.docpilot.filesystem.path.FilesystemPath;
import io.docpilot.filesystem.path.FilesystemPathNames;
import io.docpilot.filesystem.retrieval.FilesystemRetrievalHit;
import io.docpilot.filesystem.retrieval.FilesystemRetrievalOptions;
import io.docpilot.filesystem.retrieval.FilesystemRetrievalRequest;
import io.docpilot.filesystem.retrieval.FilesystemRetrievalResult;
import io.docpilot.workspace.knowledge.KnowledgeRetrievalService;
import io.docpilot.workspace.model.entity.Workspace;
import io.docpilot.workspace.model.request.UserFilesystemRetrieveCommand;
import io.docpilot.workspace.model.response.UserFilesystemDiagnosticResponse;
import io.docpilot.workspace.model.response.UserFilesystemRetrieveResponse;
import io.docpilot.workspace.processing.WorkspaceIdCodec;
import io.docpilot.workspace.repository.WorkspaceDocumentRepository;
import io.docpilot.workspace.repository.WorkspaceNodeRepository;
import io.docpilot.workspace.repository.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * User-scoped virtual filesystem facade used by AI and inline completion features.
 */
@Service
public class UserFilesystemService {

    /**
     * Logger for user filesystem retrieval lifecycle events.
     */
    private static final Logger log = LoggerFactory.getLogger(UserFilesystemService.class);

    /**
     * User-visible namespace that contains the current user's workspaces.
     */
    private static final String WORKSPACE_NAMESPACE = "/workspace";

    /**
     * Default maximum number of retrieval hits.
     */
    private static final int DEFAULT_TOP_K = 8;

    /**
     * Maximum accepted retrieval hit count.
     */
    private static final int MAX_TOP_K = 20;

    /**
     * Default maximum snippet size per hit.
     */
    private static final int DEFAULT_MAX_CHARS_PER_HIT = 500;

    /**
     * Maximum accepted snippet size per hit.
     */
    private static final int MAX_CHARS_PER_HIT = 2000;

    /**
     * Diagnostic code used when best-effort retrieval skips one workspace branch.
     */
    private static final String RETRIEVAL_FAILED = "RETRIEVAL_FAILED";

    /**
     * Repository for resolving current user's workspaces.
     */
    private final WorkspaceRepository workspaceRepository;

    /**
     * Repository used by workspace filesystem instances to resolve nodes.
     */
    private final WorkspaceNodeRepository nodeRepository;

    /**
     * Repository used by workspace filesystem instances to read document snapshots.
     */
    private final WorkspaceDocumentRepository documentRepository;

    /**
     * Current authenticated subject provider.
     */
    private final AuthContextProvider authContextProvider;

    /**
     * Codec for formatting internal workspace ids into user-visible path segments.
     */
    private final WorkspaceIdCodec idCodec;

    /**
     * Semantic retrieval service used by workspace filesystem instances.
     */
    private final KnowledgeRetrievalService retrievalService;

    public UserFilesystemService(WorkspaceRepository workspaceRepository,
                                 WorkspaceNodeRepository nodeRepository,
                                 WorkspaceDocumentRepository documentRepository,
                                 AuthContextProvider authContextProvider,
                                 WorkspaceIdCodec idCodec,
                                 KnowledgeRetrievalService retrievalService) {
        this.workspaceRepository = workspaceRepository;
        this.nodeRepository = nodeRepository;
        this.documentRepository = documentRepository;
        this.authContextProvider = authContextProvider;
        this.idCodec = idCodec;
        this.retrievalService = retrievalService;
    }

    /**
     * Retrieves ranked context from the current user's virtual filesystem.
     *
     * @param command caller path, query, limits, and failure handling strategy.
     * @return retrieval hits in user-visible path space.
     */
    public UserFilesystemRetrieveResponse retrieve(UserFilesystemRetrieveCommand command) {
        AuthSubject subject = requireSubject();
        UserFilesystemRetrieveCommand effectiveCommand = command == null
                ? new UserFilesystemRetrieveCommand()
                : command;
        String path = normalizeUserPath(effectiveCommand.getPath());
        int topK = normalizeLimit(effectiveCommand.getTopK(), DEFAULT_TOP_K, MAX_TOP_K, "topK");
        int maxCharsPerHit = normalizeLimit(
                effectiveCommand.getMaxCharsPerHit(),
                DEFAULT_MAX_CHARS_PER_HIT,
                MAX_CHARS_PER_HIT,
                "maxCharsPerHit"
        );
        UserFilesystemFailureMode failureMode = effectiveCommand.getFailureMode() == null
                ? UserFilesystemFailureMode.BEST_EFFORT
                : effectiveCommand.getFailureMode();
        List<RetrievalTarget> targets = resolveTargets(path, subject.getUserId());
        int queryLength = effectiveCommand.getQuery() == null ? 0 : effectiveCommand.getQuery().length();
        log.info("user filesystem retrieve start userId={} path={} queryLength={} topK={} maxCharsPerHit={} failureMode={} targets={}",
                subject.getUserId(), path, queryLength, topK, maxCharsPerHit, failureMode, targets.size());

        String query = effectiveCommand.getQuery() == null ? "" : effectiveCommand.getQuery();
        if (topK == 0 || query.isBlank() || targets.isEmpty()) {
            log.info("user filesystem retrieve skipped userId={} path={} reason={} targets={}",
                    subject.getUserId(), path, skipReason(topK, query, targets), targets.size());
            return new UserFilesystemRetrieveResponse(List.of(), false, null, 0L, List.of());
        }

        RetrievalAccumulator accumulator = new RetrievalAccumulator(topK);
        int targetIndex = 0;
        for (RetrievalTarget target : targets) {
            if (accumulator.isSatisfied()) {
                int skippedTargets = targets.size() - targetIndex;
                accumulator.markStoppedByTopK();
                log.info("user filesystem retrieve early stop userId={} path={} hits={} topK={} searchedMounts={} skippedTargets={}",
                        subject.getUserId(), path, accumulator.hitCount(), topK, accumulator.searchedMounts(),
                        skippedTargets);
                break;
            }
            FilesystemRetrievalOptions options = new FilesystemRetrievalOptions(
                    accumulator.remainingTopK(),
                    maxCharsPerHit
            );
            retrieveTarget(subject.getUserId(), target, query, options, failureMode, accumulator);
            targetIndex++;
        }
        UserFilesystemRetrieveResponse response = accumulator.toResponse();
        log.info("user filesystem retrieve done userId={} path={} targets={} hits={} diagnostics={} truncated={} reason={} searchedMounts={}",
                subject.getUserId(), path, targets.size(), response.hits().size(), response.diagnostics().size(),
                response.truncated(), response.truncationReason(), response.searchedMounts());
        return response;
    }

    private void retrieveTarget(Long userId,
                                RetrievalTarget target,
                                String query,
                                FilesystemRetrievalOptions options,
                                UserFilesystemFailureMode failureMode,
                                RetrievalAccumulator accumulator) {
        log.info("user filesystem retrieve target start userId={} workspaceId={} userPath={} internalPath={} topK={}",
                userId, target.workspaceId(), target.userScopedPath(), target.internalPath(), options.topK());
        WorkspaceFilesystem workspaceFilesystem = new WorkspaceFilesystem(
                target.workspaceId(),
                workspaceRepository,
                nodeRepository,
                documentRepository,
                retrievalService
        );
        try {
            FilesystemRetrievalResult result = workspaceFilesystem.retrieve(new FilesystemRetrievalRequest(
                    target.internalPath(),
                    query,
                    options
            ));
            accumulator.addResult(result, target);
            log.info("user filesystem retrieve target done userId={} workspaceId={} userPath={} hits={} truncated={} reason={} searchedMounts={}",
                    userId, target.workspaceId(), target.userScopedPath(), result.hits().size(), result.truncated(),
                    result.truncationReason(), result.searchedMounts());
        } catch (RuntimeException exception) {
            if (!failureMode.bestEffort()) {
                log.warn("user filesystem retrieve target failed userId={} workspaceId={} userPath={} failureMode={}",
                        userId, target.workspaceId(), target.userScopedPath(), failureMode, exception);
                throw exception;
            }
            log.warn("user filesystem retrieve target skipped userId={} workspaceId={} userPath={} failureMode={}",
                    userId, target.workspaceId(), target.userScopedPath(), failureMode, exception);
            accumulator.addFailure(target, exception);
        }
    }

    private List<RetrievalTarget> resolveTargets(String path, Long userId) {
        if (FilesystemPathNames.ROOT.equals(path) || WORKSPACE_NAMESPACE.equals(path)) {
            return workspaceRepository.findActiveByOwnerUserId(userId).stream()
                    .map(workspace -> new RetrievalTarget(
                            workspace.getId(),
                            workspaceMountPath(workspace.getId()),
                            workspaceMountPath(workspace.getId()),
                            FilesystemPathNames.ROOT
                    ))
                    .toList();
        }

        if (!FilesystemPath.isSameOrDescendant(WORKSPACE_NAMESPACE, path)) {
            throw new IllegalArgumentException("Unsupported filesystem namespace: " + path);
        }

        String workspaceRelativePath = FilesystemPath.relativeVirtualPath(WORKSPACE_NAMESPACE, path);
        String[] parts = workspaceRelativePath.split(FilesystemPathNames.ROOT, 2);
        if (parts.length == 0 || parts[0].isBlank()) {
            throw new IllegalArgumentException("workspace id is required in filesystem path");
        }

        Long workspaceId = parseWorkspaceId(parts[0]);
        requireOwnedWorkspace(workspaceId, userId);
        String mountPath = workspaceMountPath(workspaceId);
        String internalPath = parts.length == 1 || parts[1].isBlank()
                ? FilesystemPathNames.ROOT
                : FilesystemPathNames.ROOT + parts[1];
        return List.of(new RetrievalTarget(workspaceId, path, mountPath, internalPath));
    }

    private Workspace requireOwnedWorkspace(Long workspaceId, Long userId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new NotFoundException("Workspace not found: " + workspaceId));
        if (Boolean.TRUE.equals(workspace.getIsDeleted())) {
            throw new NotFoundException("Workspace not found: " + workspaceId);
        }
        if (!Objects.equals(workspace.getOwnerUserId(), userId)) {
            throw new ForbiddenException("Workspace access denied");
        }
        return workspace;
    }

    private Long parseWorkspaceId(String value) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException("workspace id in filesystem path must be a positive long id");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("workspace id in filesystem path must be a positive long id", exception);
        }
    }

    private String workspaceMountPath(Long workspaceId) {
        return FilesystemPath.joinVirtualPath(WORKSPACE_NAMESPACE, idCodec.format(workspaceId));
    }

    private AuthSubject requireSubject() {
        return authContextProvider.currentSubject()
                .orElseThrow(() -> new UnauthorizedException("Authentication is required"));
    }

    private String normalizeUserPath(String path) {
        try {
            if (path == null || path.isBlank()) {
                return WORKSPACE_NAMESPACE;
            }
            return FilesystemPath.normalizeVirtualPath(path);
        } catch (InvalidPathException exception) {
            throw new IllegalArgumentException(exception.getMessage(), exception);
        }
    }

    private int normalizeLimit(Integer value, int fallback, int maximum, String fieldName) {
        int normalized = value == null ? fallback : value;
        if (normalized < 0) {
            throw new IllegalArgumentException(fieldName + " must be greater than or equal to 0");
        }
        return Math.min(normalized, maximum);
    }

    private String skipReason(int topK, String query, List<RetrievalTarget> targets) {
        if (topK == 0) {
            return "topK_zero";
        }
        if (query.isBlank()) {
            return "blank_query";
        }
        if (targets.isEmpty()) {
            return "empty_scope";
        }
        return "unknown";
    }

    private record RetrievalTarget(
            Long workspaceId,
            String userScopedPath,
            String mountPath,
            String internalPath
    ) {
    }

    private static final class RetrievalAccumulator {

        /**
         * Global hit limit after merging all workspace branches.
         */
        private final int topK;

        /**
         * Mapped hits collected before global ranking.
         */
        private final List<FilesystemRetrievalHit> hits = new ArrayList<>();

        /**
         * Best-effort diagnostics collected from failed workspace branches.
         */
        private final List<UserFilesystemDiagnosticResponse> diagnostics = new ArrayList<>();

        /**
         * Workspace branches consulted by this request.
         */
        private long searchedMounts;

        /**
         * First child truncation reason seen before global topK is applied.
         */
        private String childTruncationReason;

        private RetrievalAccumulator(int topK) {
            this.topK = topK;
        }

        private void addResult(FilesystemRetrievalResult result, RetrievalTarget target) {
            searchedMounts += Math.max(1L, result.searchedMounts());
            for (FilesystemRetrievalHit hit : result.hits()) {
                hits.add(hit.withPath(FilesystemPath.joinVirtualPath(target.mountPath(), hit.path())));
            }
            if (result.truncated() && childTruncationReason == null) {
                childTruncationReason = result.truncationReason();
            }
        }

        private boolean isSatisfied() {
            return hits.size() >= topK;
        }

        private int remainingTopK() {
            return Math.max(0, topK - hits.size());
        }

        private int hitCount() {
            return hits.size();
        }

        private long searchedMounts() {
            return searchedMounts;
        }

        private void markStoppedByTopK() {
            if (childTruncationReason == null) {
                childTruncationReason = FilesystemRetrievalResult.TRUNCATED_BY_TOP_K;
            }
        }

        private void addFailure(RetrievalTarget target, RuntimeException exception) {
            searchedMounts++;
            diagnostics.add(new UserFilesystemDiagnosticResponse(
                    target.userScopedPath(),
                    RETRIEVAL_FAILED,
                    exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()
            ));
        }

        private UserFilesystemRetrieveResponse toResponse() {
            List<FilesystemRetrievalHit> rankedHits = hits.stream()
                    .sorted(Comparator
                            .comparingDouble(RetrievalAccumulator::scoreValue)
                            .reversed()
                            .thenComparing(hit -> hit.path() == null ? "" : hit.path()))
                    .toList();
            boolean topKTruncated = rankedHits.size() > topK;
            List<FilesystemRetrievalHit> limitedHits = rankedHits.stream()
                    .limit(topK)
                    .toList();
            boolean truncated = topKTruncated || childTruncationReason != null;
            String truncationReason = topKTruncated
                    ? FilesystemRetrievalResult.TRUNCATED_BY_TOP_K
                    : childTruncationReason;
            return new UserFilesystemRetrieveResponse(
                    limitedHits,
                    truncated,
                    truncationReason,
                    searchedMounts,
                    diagnostics
            );
        }

        private static double scoreValue(FilesystemRetrievalHit hit) {
            if (hit == null || hit.score() == null || hit.score().isNaN()) {
                return 0.0D;
            }
            return hit.score();
        }

    }

}
