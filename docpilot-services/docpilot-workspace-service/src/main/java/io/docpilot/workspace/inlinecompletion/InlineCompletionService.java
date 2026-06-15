package io.docpilot.workspace.inlinecompletion;

import io.docpilot.ai.AiChatModel;
import io.docpilot.ai.AiModelMetadata;
import io.docpilot.ai.AiModelRegistry;
import io.docpilot.ai.model.ChatMessage;
import io.docpilot.ai.model.ChatRequest;
import io.docpilot.common.auth.AuthContextProvider;
import io.docpilot.common.auth.AuthSubject;
import io.docpilot.common.exception.ForbiddenException;
import io.docpilot.common.exception.NotFoundException;
import io.docpilot.common.exception.UnauthorizedException;
import io.docpilot.filesystem.retrieval.FilesystemRetrievalHit;
import io.docpilot.workspace.filesystem.UserFilesystemFailureMode;
import io.docpilot.workspace.filesystem.UserFilesystemService;
import io.docpilot.workspace.model.entity.Workspace;
import io.docpilot.workspace.model.entity.WorkspaceDocument;
import io.docpilot.workspace.model.request.InlineCompletionRequest;
import io.docpilot.workspace.model.request.UserFilesystemRetrieveCommand;
import io.docpilot.workspace.model.response.UserFilesystemDiagnosticResponse;
import io.docpilot.workspace.model.response.UserFilesystemRetrieveResponse;
import io.docpilot.workspace.processing.WorkspaceIdCodec;
import io.docpilot.workspace.repository.WorkspaceDocumentRepository;
import io.docpilot.workspace.repository.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Builds model requests for inline completion from live frontend editor context.
 */
@Service
public class InlineCompletionService {

    private static final Logger log = LoggerFactory.getLogger(InlineCompletionService.class);

    private static final int RETRIEVAL_TOP_K = 4;

    private static final int RETRIEVAL_MAX_CHARS_PER_HIT = 500;

    private static final int MAX_PROMPT_TEXT_CHARS = 2400;

    private static final int MAX_BLOCK_TEXT_CHARS = 900;

    private final WorkspaceRepository workspaceRepository;

    private final WorkspaceDocumentRepository documentRepository;

    private final AuthContextProvider authContextProvider;

    private final WorkspaceIdCodec idCodec;

    private final UserFilesystemService userFilesystemService;

    private final AiModelRegistry aiModelRegistry;

    private final InlineCompletionProperties properties;

    public InlineCompletionService(WorkspaceRepository workspaceRepository,
                                   WorkspaceDocumentRepository documentRepository,
                                   AuthContextProvider authContextProvider,
                                   WorkspaceIdCodec idCodec,
                                   UserFilesystemService userFilesystemService,
                                   AiModelRegistry aiModelRegistry,
                                   InlineCompletionProperties properties) {
        this.workspaceRepository = workspaceRepository;
        this.documentRepository = documentRepository;
        this.authContextProvider = authContextProvider;
        this.idCodec = idCodec;
        this.userFilesystemService = userFilesystemService;
        this.aiModelRegistry = aiModelRegistry;
        this.properties = properties;
    }

    public InlineCompletionStream stream(InlineCompletionRequest request) {
        if (!properties.isEnabled()) {
            throw new IllegalArgumentException("Inline completion is disabled");
        }

        InlineCompletionRequest effectiveRequest = request == null
                ? new InlineCompletionRequest(null, null, null, null, List.of(), List.of(), null, null)
                : request;
        AuthSubject subject = requireSubject();
        long workspaceId = idCodec.parseRequired(effectiveRequest.workspaceId(), "workspaceId");
        long documentId = idCodec.parseRequired(effectiveRequest.documentId(), "documentId");
        Workspace workspace = requireOwnedWorkspace(workspaceId, subject);
        WorkspaceDocument document = requireActiveDocument(documentId);
        requireDocumentInWorkspace(document, workspace, subject);

        InlineCompletionTrigger trigger = InlineCompletionTrigger.parse(effectiveRequest.trigger());
        InlineCompletionShape shape = decideShape(effectiveRequest.currentBlock());
        String completionId = UUID.randomUUID().toString();
        UserFilesystemRetrieveResponse retrieval = retrieveContext(workspaceId, documentId, effectiveRequest);
        List<FilesystemRetrievalHit> hits = retrieval.hits().stream()
                .filter(hit -> !Objects.equals(String.valueOf(documentId), hit.metadata().get("documentId")))
                .toList();
        AiChatModel model = aiModelRegistry.resolve(properties.getModelId());
        ChatRequest chatRequest = buildChatRequest(effectiveRequest, hits, shape, model.metadata());

        log.info("inline completion start completionId={} userId={} workspaceId={} documentId={} shape={} trigger={} modelId={} retrieveHits={} diagnostics={}",
                completionId, subject.getUserId(), workspaceId, documentId, shape, trigger, model.id(),
                hits.size(), retrieval.diagnostics().size());
        return new InlineCompletionStream(
                completionId,
                model.id(),
                shape,
                retrieval.diagnostics(),
                model.stream(chatRequest)
        );
    }

    public static String previewText(String markdown, InlineCompletionShape shape) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        if (shape == InlineCompletionShape.CODE_LINE) {
            return markdown;
        }
        String text = decodeHtmlEntities(markdown)
                .replaceAll("(?m)^#{1,6}\\s+", "")
                .replaceAll("(?m)^\\s*(>\\s*)+", "")
                .replaceAll("(?m)^\\s*[-*+]\\s+", "")
                .replaceAll("(?m)^\\s*\\d+[.)]\\s+", "")
                .replaceAll("```[\\s\\S]*?```", "")
                .replaceAll("`([^`]*)`", "$1")
                .replaceAll("\\*\\*([^*]+)\\*\\*", "$1")
                .replaceAll("__([^_]+)__", "$1")
                .replaceAll("~~([^~]+)~~", "$1")
                .replaceAll("\\[([^]]+)]\\([^)]*\\)", "$1")
                .replace('\r', '\n');
        return text.replaceAll("\\n{3,}", "\n\n").trim();
    }

    private static String decodeHtmlEntities(String text) {
        return text
                .replace("&gt;", ">")
                .replace("&lt;", "<")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&");
    }

    private UserFilesystemRetrieveResponse retrieveContext(long workspaceId,
                                                           long documentId,
                                                           InlineCompletionRequest request) {
        UserFilesystemRetrieveCommand command = new UserFilesystemRetrieveCommand();
        command.setPath("/workspace/" + idCodec.format(workspaceId));
        command.setQuery(retrievalQuery(request));
        command.setTopK(RETRIEVAL_TOP_K);
        command.setMaxCharsPerHit(RETRIEVAL_MAX_CHARS_PER_HIT);
        command.setFailureMode(UserFilesystemFailureMode.BEST_EFFORT);
        try {
            return userFilesystemService.retrieve(command);
        } catch (RuntimeException exception) {
            log.warn("inline completion retrieval skipped workspaceId={} documentId={}", workspaceId, documentId, exception);
            return new UserFilesystemRetrieveResponse(
                    List.of(),
                    false,
                    null,
                    0,
                    List.of(new UserFilesystemDiagnosticResponse(
                            "/workspace/" + idCodec.format(workspaceId),
                            "RETRIEVAL_FAILED",
                            exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()
                    ))
            );
        }
    }

    private ChatRequest buildChatRequest(InlineCompletionRequest request,
                                         List<FilesystemRetrievalHit> hits,
                                         InlineCompletionShape shape,
                                         AiModelMetadata modelMetadata) {
        ChatRequest chatRequest = new ChatRequest();
        chatRequest.setTemperature(properties.getTemperature());
        chatRequest.setMaxOutputTokens(properties.maxOutputTokens(shape));
        chatRequest.setMessages(List.of(
                new ChatMessage("system", systemPrompt(shape)),
                new ChatMessage("user", userPrompt(request, hits, shape))
        ));
        applyProviderHints(chatRequest, modelMetadata);
        return chatRequest;
    }

    private void applyProviderHints(ChatRequest chatRequest, AiModelMetadata modelMetadata) {
        String modelName = modelMetadata == null ? "" : text(modelMetadata.modelName()).toLowerCase(Locale.ROOT);
        if (modelName.contains("qwen")) {
            chatRequest.setOption("enable_thinking", false);
        }
    }

    private String systemPrompt(InlineCompletionShape shape) {
        return """
                You are DocPilot inline completion.
                Return only the markdown fragment that should be inserted at the cursor.
                Start with the insertion immediately. Do not explain, do not include reasoning, do not add alternatives, and do not wrap the answer in a code fence unless the requested shape is CODE_LINE and the literal text requires it.
                Match the user's language, style, formatting, and surrounding document structure.
                Requested shape: %s.
                """.formatted(shape);
    }

    private String userPrompt(InlineCompletionRequest request,
                              List<FilesystemRetrievalHit> hits,
                              InlineCompletionShape shape) {
        InlineCompletionRequest.BlockContext currentBlock = request.currentBlock();
        StringBuilder prompt = new StringBuilder();
        prompt.append("Current heading path:\n")
                .append(request.headingPath().isEmpty() ? "(none)" : String.join(" > ", request.headingPath()))
                .append("\n\nCurrent block:\n")
                .append("type: ").append(text(currentBlock == null ? null : currentBlock.type())).append('\n')
                .append("text before cursor:\n")
                .append(truncate(text(currentBlock == null ? null : currentBlock.textBeforeCursor()), MAX_BLOCK_TEXT_CHARS))
                .append("\n\ntext after cursor:\n")
                .append(truncate(text(currentBlock == null ? null : currentBlock.textAfterCursor()), MAX_BLOCK_TEXT_CHARS))
                .append("\n\nNearby unsaved blocks:\n")
                .append(nearbyBlocks(request.nearbyBlocks()))
                .append("\n\nRetrieved saved workspace context:\n")
                .append(retrievedContext(hits))
                .append("\n\nWrite the next insertion as ")
                .append(shape)
                .append(" markdown. Output only the insertion.");
        return truncate(prompt.toString(), MAX_PROMPT_TEXT_CHARS);
    }

    private String nearbyBlocks(List<InlineCompletionRequest.BlockContext> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return "(none)";
        }
        StringBuilder builder = new StringBuilder();
        for (InlineCompletionRequest.BlockContext block : blocks) {
            if (block == null) {
                continue;
            }
            builder.append("- ")
                    .append(text(block.type()))
                    .append(": ")
                    .append(truncate(text(block.text()), 240))
                    .append('\n');
        }
        return builder.isEmpty() ? "(none)" : builder.toString().stripTrailing();
    }

    private String retrievedContext(List<FilesystemRetrievalHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return "(none)";
        }
        StringBuilder builder = new StringBuilder();
        int index = 1;
        for (FilesystemRetrievalHit hit : hits) {
            builder.append(index++)
                    .append(". ")
                    .append(text(hit.path()))
                    .append(" / ")
                    .append(text(hit.title()))
                    .append('\n')
                    .append(truncate(text(hit.snippet()), 360))
                    .append("\n\n");
        }
        return builder.toString().stripTrailing();
    }

    private String retrievalQuery(InlineCompletionRequest request) {
        List<String> parts = new ArrayList<>();
        if (!request.headingPath().isEmpty()) {
            parts.add(String.join(" ", request.headingPath()));
        }
        InlineCompletionRequest.BlockContext currentBlock = request.currentBlock();
        if (currentBlock != null) {
            parts.add(text(currentBlock.textBeforeCursor()));
            if (parts.stream().allMatch(String::isBlank)) {
                parts.add(text(currentBlock.text()));
            }
        }
        return truncate(String.join("\n", parts), 600);
    }

    private InlineCompletionShape decideShape(InlineCompletionRequest.BlockContext block) {
        if (block == null) {
            return InlineCompletionShape.SENTENCE;
        }
        String type = text(block.type()).toUpperCase(Locale.ROOT);
        String before = text(block.textBeforeCursor()).stripTrailing();
        if ("CODE_BLOCK".equals(type) || "DIAGRAM_BLOCK".equals(type)) {
            return InlineCompletionShape.CODE_LINE;
        }
        if ("TABLE_CELL".equals(type)) {
            return InlineCompletionShape.TABLE_CELL;
        }
        if ("LIST_ITEM".equals(type) || "TASK_LIST_ITEM".equals(type)) {
            return InlineCompletionShape.LIST_ITEM;
        }
        if ("HEADING".equals(type) || before.length() < 18) {
            return InlineCompletionShape.SHORT;
        }
        if (before.endsWith(":") || before.endsWith("\uFF1A")) {
            return InlineCompletionShape.PARAGRAPH;
        }
        return InlineCompletionShape.SENTENCE;
    }

    private Workspace requireOwnedWorkspace(Long workspaceId, AuthSubject subject) {
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

    private WorkspaceDocument requireActiveDocument(Long documentId) {
        WorkspaceDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new NotFoundException("Document not found: " + documentId));
        if (Boolean.TRUE.equals(document.getIsDeleted())) {
            throw new NotFoundException("Document not found: " + documentId);
        }
        return document;
    }

    private void requireDocumentInWorkspace(WorkspaceDocument document, Workspace workspace, AuthSubject subject) {
        if (!Objects.equals(document.getOriginWorkspaceId(), workspace.getId())
                || !Objects.equals(document.getOwnerUserId(), subject.getUserId())) {
            throw new ForbiddenException("Document access denied");
        }
    }

    private AuthSubject requireSubject() {
        return authContextProvider.currentSubject()
                .orElseThrow(() -> new UnauthorizedException("Authentication is required"));
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxChars);
    }

}
