package io.docpilot.workspace.inlinecompletion;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.docpilot.ai.AiChatModel;
import io.docpilot.ai.AiModelMetadata;
import io.docpilot.ai.AiModelRegistry;
import io.docpilot.ai.model.ChatChoice;
import io.docpilot.ai.model.ChatMessage;
import io.docpilot.ai.model.ChatRequest;
import io.docpilot.ai.model.ChatResponse;
import io.docpilot.ai.model.JsonSchema;
import io.docpilot.ai.model.JsonSchemaResponseFormat;
import io.docpilot.common.auth.AuthContextProvider;
import io.docpilot.common.auth.AuthSubject;
import io.docpilot.common.exception.ForbiddenException;
import io.docpilot.common.exception.NotFoundException;
import io.docpilot.common.exception.UnauthorizedException;
import io.docpilot.filesystem.retrieval.FilesystemRetrievalHit;
import io.docpilot.user.application.UserSettingManager;
import io.docpilot.user.model.UserSettingKeys;
import io.docpilot.workspace.filesystem.UserFilesystemFailureMode;
import io.docpilot.workspace.filesystem.UserFilesystemService;
import io.docpilot.workspace.model.entity.Workspace;
import io.docpilot.workspace.model.entity.WorkspaceDocument;
import io.docpilot.workspace.model.request.InlineCompletionRequest;
import io.docpilot.workspace.model.request.UserFilesystemRetrieveCommand;
import io.docpilot.workspace.model.response.InlineCompletionCandidateResponse;
import io.docpilot.workspace.model.response.InlineCompletionCompleteResponse;
import io.docpilot.workspace.model.response.UserFilesystemDiagnosticResponse;
import io.docpilot.workspace.model.response.UserFilesystemRetrieveResponse;
import io.docpilot.workspace.processing.WorkspaceIdCodec;
import io.docpilot.workspace.repository.WorkspaceDocumentRepository;
import io.docpilot.workspace.repository.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Builds model requests for inline completion from live frontend editor context.
 */
@Service
public class InlineCompletionService {

    private static final Logger log = LoggerFactory.getLogger(InlineCompletionService.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final int DEFAULT_CANDIDATE_COUNT = 3;

    private static final int MIN_CANDIDATE_COUNT = 1;

    private static final int MAX_CANDIDATE_COUNT = 5;

    private static final int RETRIEVAL_TOP_K = 4;

    private static final int RETRIEVAL_MAX_CHARS_PER_HIT = 500;

    private static final int MAX_PROMPT_TEXT_CHARS = 2400;

    private static final int MAX_BLOCK_TEXT_CHARS = 900;

    private static final JsonSchemaResponseFormat CANDIDATES_RESPONSE_FORMAT = JsonSchemaResponseFormat.of(
            "inline_completion_candidates",
            JsonSchema.builder()
                    .prop(JsonSchema.arrayProp("candidates", JsonSchema.objectItem()
                                    .prop(JsonSchema.stringProp("markdown")
                                            .description("Markdown fragment to insert at the cursor.")))
                            .minItems(1)
                            .maxItems(MAX_CANDIDATE_COUNT))
                    .build()
    );

    private final WorkspaceRepository workspaceRepository;

    private final WorkspaceDocumentRepository documentRepository;

    private final AuthContextProvider authContextProvider;

    private final WorkspaceIdCodec idCodec;

    private final UserFilesystemService userFilesystemService;

    private final AiModelRegistry aiModelRegistry;

    private final InlineCompletionProperties properties;

    /**
     * Current-user settings used for per-user generation limits.
     */
    private final UserSettingManager userSettingManager;

    public InlineCompletionService(WorkspaceRepository workspaceRepository,
                                   WorkspaceDocumentRepository documentRepository,
                                   AuthContextProvider authContextProvider,
                                   WorkspaceIdCodec idCodec,
                                   UserFilesystemService userFilesystemService,
                                   AiModelRegistry aiModelRegistry,
                                   InlineCompletionProperties properties,
                                   UserSettingManager userSettingManager) {
        this.workspaceRepository = workspaceRepository;
        this.documentRepository = documentRepository;
        this.authContextProvider = authContextProvider;
        this.idCodec = idCodec;
        this.userFilesystemService = userFilesystemService;
        this.aiModelRegistry = aiModelRegistry;
        this.properties = properties;
        this.userSettingManager = userSettingManager;
    }

    public InlineCompletionCompleteResponse complete(InlineCompletionRequest request) {
        if (!properties.isEnabled()) {
            throw new IllegalArgumentException("Inline completion is disabled");
        }

        InlineCompletionRequest effectiveRequest = request == null
                ? new InlineCompletionRequest(null, null, null, null, List.of(), List.of(), null, null, null)
                : request;
        AuthSubject subject = requireSubject();
        int candidateCount = candidateCount(subject.getUserId(), effectiveRequest.candidateCount());
        long workspaceId = idCodec.parseRequired(effectiveRequest.workspaceId(), "workspaceId");
        long documentId = idCodec.parseRequired(effectiveRequest.documentId(), "documentId");
        Workspace workspace = requireOwnedWorkspace(workspaceId, subject);
        WorkspaceDocument document = requireActiveDocument(documentId);
        requireDocumentInWorkspace(document, workspace, subject);

        InlineCompletionTrigger trigger = InlineCompletionTrigger.parse(effectiveRequest.trigger());
        InlineCompletionShape shape = decideShape(effectiveRequest.currentBlock());
        int candidateTokenLimit = candidateTokenLimit(subject.getUserId(), shape);
        String completionId = UUID.randomUUID().toString();
        UserFilesystemRetrieveResponse retrieval = retrieveContext(completionId, workspaceId, documentId, effectiveRequest);
        logRetrievalHits(completionId, documentId, retrieval);
        List<FilesystemRetrievalHit> hits = retrieval.hits().stream()
                .filter(hit -> !isCurrentDocumentHit(hit, documentId))
                .toList();
        logSelectedRetrievalHits(completionId, hits);
        AiChatModel model = aiModelRegistry.resolve(properties.getModelId());
        String systemPrompt = systemPrompt(shape, candidateCount, candidateTokenLimit);
        String userPrompt = userPrompt(effectiveRequest, hits, shape, candidateCount, candidateTokenLimit);
        ChatRequest chatRequest = buildChatRequest(systemPrompt, userPrompt, model.metadata(),
                candidateCount, candidateTokenLimit);

        log.info("inline completion start completionId={} userId={} workspaceId={} documentId={} shape={} trigger={} modelId={} candidateCount={} candidateTokenLimit={} maxTokens={} promptChars={} retrieveHits={} retrievedHitPaths={} diagnostics={}",
                completionId, subject.getUserId(), workspaceId, documentId, shape, trigger, model.id(),
                candidateCount, candidateTokenLimit, chatRequest.getMaxOutputTokens(), userPrompt.length(), hits.size(),
                hitPaths(hits), retrieval.diagnostics().size());
        ChatResponse response = model.chat(chatRequest);
        String modelText = assistantText(response);
        List<InlineCompletionCandidateResponse> candidates = candidatesFromModelText(modelText, shape, candidateCount);
        log.info("inline completion complete completionId={} modelId={} shape={} candidateCountRequested={} candidateCountReturned={} diagnostics={} previews={} markdowns={}",
                completionId, model.id(), shape, candidateCount, candidates.size(), retrieval.diagnostics().size(),
                candidates.stream().map(InlineCompletionCandidateResponse::previewText).toList(),
                candidates.stream()
                        .map(candidate -> escapedPreview(candidate.markdown(), 300))
                        .toList());
        return new InlineCompletionCompleteResponse(
                completionId,
                model.id(),
                shape,
                candidates,
                retrieval.diagnostics()
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

    private UserFilesystemRetrieveResponse retrieveContext(String completionId,
                                                           long workspaceId,
                                                           long documentId,
                                                           InlineCompletionRequest request) {
        UserFilesystemRetrieveCommand command = new UserFilesystemRetrieveCommand();
        command.setPath("/workspace/" + idCodec.format(workspaceId));
        String query = retrievalQuery(request);
        command.setQuery(query);
        command.setTopK(RETRIEVAL_TOP_K);
        command.setMaxCharsPerHit(RETRIEVAL_MAX_CHARS_PER_HIT);
        command.setFailureMode(UserFilesystemFailureMode.BEST_EFFORT);
        log.info("inline completion retrieval request completionId={} workspaceId={} documentId={} path={} queryLength={} topK={} maxCharsPerHit={} failureMode={}",
                completionId, workspaceId, documentId, command.getPath(), query.length(),
                command.getTopK(), command.getMaxCharsPerHit(), command.getFailureMode());
        try {
            return userFilesystemService.retrieve(command);
        } catch (RuntimeException exception) {
            log.warn("inline completion retrieval skipped completionId={} workspaceId={} documentId={} errorType={} message={}",
                    completionId,
                    workspaceId,
                    documentId,
                    exception.getClass().getSimpleName(),
                    text(exception.getMessage()));
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

    private void logRetrievalHits(String completionId,
                                  long documentId,
                                  UserFilesystemRetrieveResponse retrieval) {
        long currentDocumentHits = retrieval.hits().stream()
                .filter(hit -> isCurrentDocumentHit(hit, documentId))
                .count();
        log.info("inline completion retrieval summary completionId={} rawHits={} currentDocumentHits={} selectableHits={} diagnostics={} truncated={} truncationReason={} searchedMounts={} rawHitPaths={}",
                completionId,
                retrieval.hits().size(),
                currentDocumentHits,
                retrieval.hits().size() - currentDocumentHits,
                retrieval.diagnostics().size(),
                retrieval.truncated(),
                retrieval.truncationReason(),
                retrieval.searchedMounts(),
                hitPaths(retrieval.hits()));
        for (int i = 0; i < retrieval.hits().size(); i++) {
            FilesystemRetrievalHit hit = retrieval.hits().get(i);
            boolean currentDocument = isCurrentDocumentHit(hit, documentId);
            log.debug("inline completion retrieval raw hit completionId={} index={} currentDocument={} path={} title={} score={} hitDocumentId={} headingPath={} snippetPreview={}",
                    completionId,
                    i,
                    currentDocument,
                    hit.path(),
                    text(hit.title()),
                    hit.score(),
                    hit.metadata().get("documentId"),
                    hit.headingPath(),
                    singleLinePreview(hit.snippet(), 160));
        }
        retrieval.diagnostics().forEach(diagnostic -> log.warn(
                "inline completion retrieval diagnostic completionId={} path={} code={} message={}",
                completionId,
                diagnostic.path(),
                diagnostic.code(),
                diagnostic.message()
        ));
    }

    private void logSelectedRetrievalHits(String completionId, List<FilesystemRetrievalHit> hits) {
        log.info("inline completion retrieval selected completionId={} selectedHits={} selectedHitPaths={}",
                completionId, hits.size(), hitPaths(hits));
        for (int i = 0; i < hits.size(); i++) {
            FilesystemRetrievalHit hit = hits.get(i);
            log.debug("inline completion retrieval selected hit completionId={} index={} path={} title={} score={} headingPath={} snippetPreview={}",
                    completionId,
                    i,
                    hit.path(),
                    text(hit.title()),
                    hit.score(),
                    hit.headingPath(),
                    singleLinePreview(hit.snippet(), 160));
        }
    }

    /**
     * Inline completion receives the live current document from the frontend, so indexed hits from the same document
     * are treated as stale auxiliary context and removed before prompting the model.
     */
    private boolean isCurrentDocumentHit(FilesystemRetrievalHit hit, long documentId) {
        return Objects.equals(String.valueOf(documentId), hit.metadata().get("documentId"));
    }

    private int candidateCount(Long userId, Integer requestedCount) {
        Integer effectiveCount = requestedCount;
        if (effectiveCount == null) {
            effectiveCount = userSettingManager.findUserValue(userId, UserSettingKeys.INLINE_COMPLETION_CANDIDATE_COUNT)
                    .filter(Number.class::isInstance)
                    .map(Number.class::cast)
                    .map(Number::intValue)
                    .orElse(DEFAULT_CANDIDATE_COUNT);
        }
        return Math.max(MIN_CANDIDATE_COUNT, Math.min(MAX_CANDIDATE_COUNT, effectiveCount));
    }

    private List<String> hitPaths(List<FilesystemRetrievalHit> hits) {
        return hits.stream()
                .map(FilesystemRetrievalHit::path)
                .toList();
    }

    private String assistantText(ChatResponse response) {
        if (response == null || response.getChoices() == null) {
            return "";
        }
        return response.getChoices().stream()
                .filter(Objects::nonNull)
                .map(ChatChoice::getMessage)
                .filter(Objects::nonNull)
                .map(ChatMessage::getContent)
                .map(this::contentText)
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElse("");
    }

    private String contentText(Object content) {
        if (content == null) {
            return "";
        }
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof Iterable<?> values) {
            StringBuilder builder = new StringBuilder();
            for (Object value : values) {
                builder.append(contentText(value));
            }
            return builder.toString();
        }
        if (content instanceof Map<?, ?> map) {
            Object text = map.get("text");
            if (text == null) {
                text = map.get("content");
            }
            if (text != null) {
                return contentText(text);
            }
            try {
                return OBJECT_MAPPER.writeValueAsString(map);
            } catch (JacksonException exception) {
                return String.valueOf(map);
            }
        }
        return String.valueOf(content);
    }

    private List<InlineCompletionCandidateResponse> candidatesFromModelText(String modelText,
                                                                            InlineCompletionShape shape,
                                                                            int candidateCount) {
        List<String> markdowns = parseCandidateMarkdowns(modelText);
        if (markdowns.isEmpty() && modelText != null && !modelText.isBlank()) {
            String rawText = stripJsonFence(modelText).strip();
            if (isLikelyStructuredCandidateResponse(rawText)) {
                log.warn("inline completion ignored malformed structured candidate response rawPreview={}",
                        singleLinePreview(rawText, 160));
            } else {
                markdowns = List.of(stripJsonFence(modelText));
            }
        }

        Set<String> seen = new LinkedHashSet<>();
        List<InlineCompletionCandidateResponse> candidates = new ArrayList<>();
        for (String markdown : markdowns) {
            String sanitized = sanitizeCandidateMarkdown(markdown);
            String key = sanitized.strip();
            if (key.isBlank() || !seen.add(key)) {
                continue;
            }
            candidates.add(new InlineCompletionCandidateResponse(
                    candidates.size(),
                    sanitized,
                    previewText(sanitized, shape)
            ));
            if (candidates.size() >= candidateCount) {
                break;
            }
        }
        return candidates;
    }

    private List<String> parseCandidateMarkdowns(String modelText) {
        if (modelText == null || modelText.isBlank()) {
            return List.of();
        }
        String jsonText = stripJsonFence(modelText).strip();
        try {
            JsonNode root = OBJECT_MAPPER.readTree(jsonText);
            JsonNode candidates = root.isArray() ? root : root.get("candidates");
            if (candidates != null && candidates.isArray()) {
                List<String> markdowns = new ArrayList<>();
                candidates.forEach(candidate -> {
                    if (candidate.isTextual()) {
                        markdowns.add(candidate.asText());
                        return;
                    }
                    JsonNode markdown = candidate.get("markdown");
                    if (markdown != null && markdown.isTextual()) {
                        markdowns.add(markdown.asText());
                    }
                });
                return markdowns;
            }
            JsonNode markdown = root.get("markdown");
            if (markdown != null && markdown.isTextual()) {
                return List.of(markdown.asText());
            }
        } catch (JacksonException exception) {
            List<String> recoveredMarkdowns = recoverMarkdownFields(jsonText);
            if (!recoveredMarkdowns.isEmpty()) {
                log.warn("inline completion recovered candidates from malformed JSON recoveredCount={} rawPreview={}",
                        recoveredMarkdowns.size(), singleLinePreview(modelText, 160));
                return recoveredMarkdowns;
            }
            log.debug("inline completion candidate JSON parse failed rawPreview={}", singleLinePreview(modelText, 160));
        }
        return List.of();
    }

    private boolean isLikelyStructuredCandidateResponse(String modelText) {
        if (modelText == null || modelText.isBlank()) {
            return false;
        }
        String text = modelText.stripLeading();
        return (text.startsWith("{") || text.startsWith("["))
                && (text.contains("\"candidates\"") || text.contains("\"markdown\""));
    }

    /**
     * Model output can be cut off before a full JSON object is closed. In that case we salvage
     * complete markdown string fields, but never expose the raw JSON wrapper as a completion.
     */
    private List<String> recoverMarkdownFields(String jsonText) {
        List<String> markdowns = new ArrayList<>();
        int searchIndex = 0;
        while (searchIndex < jsonText.length()) {
            int keyStart = jsonText.indexOf("\"markdown\"", searchIndex);
            if (keyStart < 0) {
                break;
            }
            int colon = skipWhitespace(jsonText, keyStart + "\"markdown\"".length());
            if (colon >= jsonText.length() || jsonText.charAt(colon) != ':') {
                searchIndex = keyStart + 1;
                continue;
            }
            int valueStart = skipWhitespace(jsonText, colon + 1);
            if (valueStart >= jsonText.length() || jsonText.charAt(valueStart) != '"') {
                searchIndex = keyStart + 1;
                continue;
            }
            ExtractedJsonString extracted = extractJsonString(jsonText, valueStart);
            if (extracted == null) {
                break;
            }
            markdowns.add(extracted.value());
            searchIndex = extracted.nextIndex();
        }
        return markdowns;
    }

    private int skipWhitespace(String text, int index) {
        int current = index;
        while (current < text.length() && Character.isWhitespace(text.charAt(current))) {
            current++;
        }
        return current;
    }

    private ExtractedJsonString extractJsonString(String text, int quoteStart) {
        boolean escaped = false;
        for (int index = quoteStart + 1; index < text.length(); index++) {
            char character = text.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (character == '\\') {
                escaped = true;
                continue;
            }
            if (character == '"') {
                String rawString = text.substring(quoteStart, index + 1);
                try {
                    return new ExtractedJsonString(OBJECT_MAPPER.readValue(rawString, String.class), index + 1);
                } catch (JacksonException exception) {
                    return null;
                }
            }
        }
        return null;
    }

    private record ExtractedJsonString(String value, int nextIndex) {
    }

    private String sanitizeCandidateMarkdown(String markdown) {
        return stripJsonFence(text(markdown))
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .stripTrailing();
    }

    private String stripJsonFence(String value) {
        String trimmed = text(value).strip();
        if (!trimmed.startsWith("```")) {
            return value == null ? "" : value;
        }
        int firstLineEnd = trimmed.indexOf('\n');
        int lastFenceStart = trimmed.lastIndexOf("```");
        if (firstLineEnd == -1 || lastFenceStart <= firstLineEnd) {
            return trimmed;
        }
        return trimmed.substring(firstLineEnd + 1, lastFenceStart).strip();
    }

    private ChatRequest buildChatRequest(String systemPrompt,
                                         String userPrompt,
                                         AiModelMetadata modelMetadata,
                                         int candidateCount,
                                         int candidateTokenLimit) {
        ChatRequest chatRequest = new ChatRequest();
        chatRequest.setTemperature(properties.getTemperature());
        chatRequest.setMaxOutputTokens(requestMaxOutputTokens(candidateTokenLimit, candidateCount));
        chatRequest.setResponseFormat(CANDIDATES_RESPONSE_FORMAT);
        chatRequest.setMessages(List.of(
                new ChatMessage("system", systemPrompt),
                new ChatMessage("user", userPrompt)
        ));
        applyProviderHints(chatRequest, modelMetadata);
        return chatRequest;
    }

    private int candidateTokenLimit(Long userId, InlineCompletionShape shape) {
        String key = maxOutputTokensSettingKey(shape);
        return userSettingManager.findUserValue(userId, key)
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .map(Number::intValue)
                .orElseGet(() -> properties.maxOutputTokens(shape));
    }

    /**
     * User settings constrain each candidate in the prompt. The request cap only prevents runaway
     * output and leaves enough room for the JSON wrapper, escaping, and closing braces.
     */
    private int requestMaxOutputTokens(int candidateTokenLimit, int candidateCount) {
        return candidateTokenLimit * candidateCount + 160;
    }

    private String maxOutputTokensSettingKey(InlineCompletionShape shape) {
        return switch (shape) {
            case SHORT -> UserSettingKeys.INLINE_COMPLETION_MAX_OUTPUT_TOKENS_SHORT;
            case SENTENCE -> UserSettingKeys.INLINE_COMPLETION_MAX_OUTPUT_TOKENS_SENTENCE;
            case PARAGRAPH -> UserSettingKeys.INLINE_COMPLETION_MAX_OUTPUT_TOKENS_PARAGRAPH;
            case LIST_ITEM -> UserSettingKeys.INLINE_COMPLETION_MAX_OUTPUT_TOKENS_LIST_ITEM;
            case TABLE_CELL -> UserSettingKeys.INLINE_COMPLETION_MAX_OUTPUT_TOKENS_TABLE_CELL;
            case CODE_LINE -> UserSettingKeys.INLINE_COMPLETION_MAX_OUTPUT_TOKENS_CODE_LINE;
        };
    }

    private void applyProviderHints(ChatRequest chatRequest, AiModelMetadata modelMetadata) {
        String modelName = modelMetadata == null ? "" : text(modelMetadata.modelName()).toLowerCase(Locale.ROOT);
        if (modelName.contains("qwen")) {
            chatRequest.setOption("enable_thinking", false);
        }
    }

    private String systemPrompt(InlineCompletionShape shape, int candidateCount, int candidateTokenLimit) {
        return applyTemplate(completePrompts().getSystem(), promptVariables(
                shape,
                candidateCount,
                candidateTokenLimit,
                "",
                "",
                "",
                "",
                "",
                "",
                ""
        ));
    }

    private String userPrompt(InlineCompletionRequest request,
                              List<FilesystemRetrievalHit> hits,
                              InlineCompletionShape shape,
                              int candidateCount,
                              int candidateTokenLimit) {
        InlineCompletionRequest.BlockContext currentBlock = request.currentBlock();
        String headingPath = request.headingPath().isEmpty() ? "(none)" : String.join(" > ", request.headingPath());
        String prompt = applyTemplate(completePrompts().getUser(), promptVariables(
                shape,
                candidateCount,
                candidateTokenLimit,
                headingPath,
                text(currentBlock == null ? null : currentBlock.type()),
                truncate(text(currentBlock == null ? null : currentBlock.textBeforeCursor()), MAX_BLOCK_TEXT_CHARS),
                truncate(text(currentBlock == null ? null : currentBlock.textAfterCursor()), MAX_BLOCK_TEXT_CHARS),
                nearbyBlocks(request.nearbyBlocks()),
                retrievedContext(hits),
                "{\"candidates\":[{\"markdown\":\"...\"}]}"
        ));
        return truncate(prompt, MAX_PROMPT_TEXT_CHARS);
    }

    private InlineCompletionProperties.CompletePromptProperties completePrompts() {
        InlineCompletionProperties.PromptProperties prompts = properties.getPrompts();
        if (prompts == null || prompts.getComplete() == null) {
            throw new IllegalStateException("Inline completion prompts are not configured");
        }
        InlineCompletionProperties.CompletePromptProperties complete = prompts.getComplete();
        if (isBlank(complete.getSystem())) {
            throw new IllegalStateException("Inline completion system prompt is not configured");
        }
        if (isBlank(complete.getUser())) {
            throw new IllegalStateException("Inline completion user prompt is not configured");
        }
        return complete;
    }

    private Map<String, String> promptVariables(InlineCompletionShape shape,
                                                int candidateCount,
                                                int candidateTokenLimit,
                                                String headingPath,
                                                String currentBlockType,
                                                String textBeforeCursor,
                                                String textAfterCursor,
                                                String nearbyBlocks,
                                                String retrievedContext,
                                                String responseJsonShape) {
        return Map.of(
                "shape", String.valueOf(shape),
                "candidateCount", String.valueOf(candidateCount),
                "candidateTokenLimit", String.valueOf(candidateTokenLimit),
                "headingPath", text(headingPath),
                "currentBlockType", text(currentBlockType),
                "textBeforeCursor", text(textBeforeCursor),
                "textAfterCursor", text(textAfterCursor),
                "nearbyBlocks", text(nearbyBlocks),
                "retrievedContext", text(retrievedContext),
                "responseJsonShape", text(responseJsonShape)
        );
    }

    private String applyTemplate(String template, Map<String, String> variables) {
        String result = template == null ? "" : template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String truncate(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxChars);
    }

    private String singleLinePreview(String value, int maxChars) {
        return truncate(text(value).replaceAll("\\s+", " ").strip(), maxChars);
    }

    private String escapedPreview(String value, int maxChars) {
        return truncate(text(value)
                .replace("\\", "\\\\")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .strip(), maxChars);
    }

}
