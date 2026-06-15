package io.docpilot.workspace.inlinecompletion;

import io.docpilot.ai.AiChatModel;
import io.docpilot.ai.AiModelMetadata;
import io.docpilot.ai.AiModelRegistry;
import io.docpilot.ai.model.ChatChoice;
import io.docpilot.ai.model.ChatDelta;
import io.docpilot.ai.model.ChatMessage;
import io.docpilot.ai.model.ChatRequest;
import io.docpilot.ai.model.ChatResponse;
import io.docpilot.ai.model.ChatStreamEvent;
import io.docpilot.ai.model.ChatStreamEventType;
import io.docpilot.common.auth.AuthContextProvider;
import io.docpilot.common.auth.AuthSubject;
import io.docpilot.filesystem.retrieval.FilesystemRetrievalHit;
import io.docpilot.user.application.UserSettingManager;
import io.docpilot.user.model.UserSettingKeys;
import io.docpilot.user.model.UserSettingRecord;
import io.docpilot.user.repository.UserSettingRepository;
import io.docpilot.workspace.filesystem.UserFilesystemFailureMode;
import io.docpilot.workspace.filesystem.UserFilesystemService;
import io.docpilot.workspace.model.entity.Workspace;
import io.docpilot.workspace.model.entity.WorkspaceDocument;
import io.docpilot.workspace.model.request.InlineCompletionRequest;
import io.docpilot.workspace.model.request.UserFilesystemRetrieveCommand;
import io.docpilot.workspace.model.response.InlineCompletionCompleteResponse;
import io.docpilot.workspace.model.response.UserFilesystemRetrieveResponse;
import io.docpilot.workspace.processing.WorkspaceIdCodec;
import io.docpilot.workspace.repository.WorkspaceDocumentRepository;
import io.docpilot.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class InlineCompletionServiceTest {

    private static final long USER_ID = 7L;
    private static final long WORKSPACE_ID = 11L;
    private static final long DOCUMENT_ID = 22L;

    private InMemoryWorkspaceRepository workspaceRepository;
    private InMemoryWorkspaceDocumentRepository documentRepository;
    private CapturingUserFilesystemService filesystemService;
    private CapturingChatModel chatModel;
    private InMemoryUserSettingRepository userSettingRepository;
    private InlineCompletionService service;

    @BeforeEach
    void setUp() {
        workspaceRepository = new InMemoryWorkspaceRepository();
        documentRepository = new InMemoryWorkspaceDocumentRepository();
        filesystemService = new CapturingUserFilesystemService();
        chatModel = new CapturingChatModel();
        userSettingRepository = new InMemoryUserSettingRepository();

        workspaceRepository.save(workspace(WORKSPACE_ID, USER_ID));
        documentRepository.save(document(DOCUMENT_ID, WORKSPACE_ID, USER_ID));

        service = new InlineCompletionService(
                workspaceRepository,
                documentRepository,
                authContext(USER_ID),
                new WorkspaceIdCodec(),
                filesystemService,
                new AiModelRegistry("inline-test", List.of(chatModel)),
                inlineCompletionProperties(),
                new UserSettingManager(userSettingRepository)
        );
    }

    @Test
    void filtersCurrentDocumentRetrievalHitsBeforePromptingModel() {
        filesystemService.response = new UserFilesystemRetrieveResponse(
                List.of(
                        hit("/workspace/11/current.md", "Stale current document", DOCUMENT_ID),
                        hit("/workspace/11/other.md", "Useful external context", 33L)
                ),
                false,
                null,
                1,
                List.of()
        );

        InlineCompletionCompleteResponse response = service.complete(request(
                "PARAGRAPH",
                "Write a summary for this paragraph",
                ""
        ));

        assertThat(response.shape()).isEqualTo(InlineCompletionShape.SENTENCE);
        assertThat(response.candidates()).hasSize(2);
        assertThat(filesystemService.command.getPath()).isEqualTo("/workspace/11");
        assertThat(filesystemService.command.getTopK()).isEqualTo(4);
        assertThat(filesystemService.command.getMaxCharsPerHit()).isEqualTo(500);
        assertThat(filesystemService.command.getFailureMode()).isEqualTo(UserFilesystemFailureMode.BEST_EFFORT);
        String prompt = userPrompt(chatModel.request);
        assertThat(prompt).contains("Useful external context");
        assertThat(prompt).doesNotContain("Stale current document");
    }

    @Test
    void retrievalFailureBecomesDiagnosticAndModelStillCompletes() {
        filesystemService.failure = new IllegalStateException("retrieval down");

        InlineCompletionCompleteResponse response = service.complete(request("PARAGRAPH", "Continue this", ""));

        assertThat(response.diagnostics()).hasSize(1);
        assertThat(response.diagnostics().getFirst().code()).isEqualTo("RETRIEVAL_FAILED");
        assertThat(response.candidates()).isNotEmpty();
        assertThat(chatModel.request).isNotNull();
    }

    @Test
    void codeBlockUsesCodeLineShapeAndTokenLimit() {
        InlineCompletionCompleteResponse response = service.complete(request("CODE_BLOCK", "const value = ", ""));

        assertThat(response.shape()).isEqualTo(InlineCompletionShape.CODE_LINE);
        assertThat(chatModel.request.getMaxOutputTokens()).isEqualTo(448);
        assertThat(userPrompt(chatModel.request)).contains("CODE_LINE markdown");
        assertThat(userPrompt(chatModel.request)).contains("around 96 tokens or less");
    }

    @Test
    void userSettingOverridesShapeTokenLimit() {
        userSettingRepository.saveAll(USER_ID, Map.of(
                UserSettingKeys.INLINE_COMPLETION_MAX_OUTPUT_TOKENS_CODE_LINE,
                "144"
        ));

        service.complete(request("CODE_BLOCK", "const value = ", ""));

        assertThat(chatModel.request.getMaxOutputTokens()).isEqualTo(592);
        assertThat(userPrompt(chatModel.request)).contains("around 144 tokens or less");
    }

    @Test
    void userSettingProvidesDefaultCandidateCountWhenRequestOmitsIt() {
        userSettingRepository.saveAll(USER_ID, Map.of(
                UserSettingKeys.INLINE_COMPLETION_CANDIDATE_COUNT,
                "4"
        ));

        service.complete(request("PARAGRAPH", "Continue this paragraph now", ""));

        assertThat(chatModel.request.getMaxOutputTokens()).isEqualTo(416);
        assertThat(userPrompt(chatModel.request)).contains("Write up to 4 candidates");
        assertThat(userPrompt(chatModel.request)).contains("around 64 tokens or less");
    }

    @Test
    void usesConfiguredPromptTemplates() {
        service.complete(request("PARAGRAPH", "Continue this paragraph now", ""));

        assertThat(chatModel.request.getMessages().get(0).getContent())
                .isEqualTo("SYSTEM count=3 limit=64 shape=SENTENCE");
        assertThat(userPrompt(chatModel.request))
                .contains("Heading=Spec")
                .contains("BlockType=PARAGRAPH")
                .contains("Before=Continue this paragraph now")
                .contains("Retrieved=(none)")
                .contains("Shape={\"candidates\":[{\"markdown\":\"...\"}]}");
    }

    @Test
    void qwenModelDisablesThinkingForLowLatencyInlineCompletion() {
        chatModel.modelName = "qwen3.6-plus";

        service.complete(request("PARAGRAPH", "Continue this paragraph now", ""));

        assertThat(chatModel.request.getOptions()).containsEntry("enable_thinking", false);
    }

    @Test
    void clampsCandidateCountAndTruncatesParsedCandidates() {
        chatModel.responseText = """
                {"candidates":[
                  {"markdown":" first"},
                  {"markdown":" second"},
                  {"markdown":" second"},
                  {"markdown":" third"},
                  {"markdown":" fourth"},
                  {"markdown":" fifth"},
                  {"markdown":" sixth"}
                ]}
                """;

        InlineCompletionCompleteResponse response = service.complete(request(
                "PARAGRAPH",
                "Continue this paragraph now",
                "",
                9
        ));

        assertThat(response.candidates())
                .extracting(candidate -> candidate.markdown())
                .containsExactly(" first", " second", " third", " fourth", " fifth");
        assertThat(chatModel.request.getMaxOutputTokens()).isEqualTo(480);
    }

    @Test
    void malformedJsonFallsBackToSingleCandidate() {
        chatModel.responseText = "plain fallback";

        InlineCompletionCompleteResponse response = service.complete(request(
                "PARAGRAPH",
                "Continue this paragraph now",
                "",
                3
        ));

        assertThat(response.candidates()).hasSize(1);
        assertThat(response.candidates().getFirst().markdown()).isEqualTo("plain fallback");
    }

    @Test
    void truncatedJsonRecoversCompleteMarkdownCandidates() {
        chatModel.responseText = """
                {"candidates":[
                  {"markdown":" first"},
                  {"markdown":" second"},
                  {"markdown":"\\n\\n- incomplete
                """;

        InlineCompletionCompleteResponse response = service.complete(request(
                "PARAGRAPH",
                "Continue this paragraph now",
                "",
                3
        ));

        assertThat(response.candidates())
                .extracting(candidate -> candidate.markdown())
                .containsExactly(" first", " second");
    }

    @Test
    void malformedStructuredJsonIsNotDisplayedAsCompletion() {
        chatModel.responseText = """
                {"candidates":[
                """;

        InlineCompletionCompleteResponse response = service.complete(request(
                "PARAGRAPH",
                "Continue this paragraph now",
                "",
                3
        ));

        assertThat(response.candidates()).isEmpty();
    }

    @Test
    void previewTextDecodesEscapedBlockquoteMarkersForInlineShapes() {
        assertThat(InlineCompletionService.previewText("&gt; &gt; &gt; nested quote", InlineCompletionShape.SENTENCE))
                .isEqualTo("nested quote");
    }

    private static InlineCompletionRequest request(String blockType, String beforeCursor, String afterCursor) {
        return request(blockType, beforeCursor, afterCursor, null);
    }

    private static InlineCompletionRequest request(String blockType,
                                                   String beforeCursor,
                                                   String afterCursor,
                                                   Integer candidateCount) {
        return new InlineCompletionRequest(
                String.valueOf(WORKSPACE_ID),
                String.valueOf(DOCUMENT_ID),
                new InlineCompletionRequest.CursorContext(1, 1),
                new InlineCompletionRequest.BlockContext(
                        "block-1",
                        blockType,
                        beforeCursor + afterCursor,
                        beforeCursor,
                        afterCursor
                ),
                List.of("Spec"),
                List.of(),
                "IDLE",
                "test",
                candidateCount
        );
    }

    private static InlineCompletionProperties inlineCompletionProperties() {
        InlineCompletionProperties properties = new InlineCompletionProperties();
        properties.getPrompts().getComplete().setSystem("SYSTEM count={{candidateCount}} limit={{candidateTokenLimit}} shape={{shape}}");
        properties.getPrompts().getComplete().setUser("""
                Heading={{headingPath}}
                BlockType={{currentBlockType}}
                Before={{textBeforeCursor}}
                After={{textAfterCursor}}
                Nearby={{nearbyBlocks}}
                Retrieved={{retrievedContext}}
                Write up to {{candidateCount}} candidates as {{shape}} markdown around {{candidateTokenLimit}} tokens or less.
                Shape={{responseJsonShape}}
                """);
        return properties;
    }

    private static FilesystemRetrievalHit hit(String path, String snippet, long documentId) {
        return new FilesystemRetrievalHit(
                path,
                "Doc " + documentId,
                snippet,
                1.0D,
                List.of("Heading"),
                Map.of("documentId", String.valueOf(documentId))
        );
    }

    private static Workspace workspace(Long id, Long ownerUserId) {
        Workspace workspace = new Workspace();
        workspace.setId(id);
        workspace.setOwnerUserId(ownerUserId);
        workspace.setName("Workspace " + id);
        workspace.setRootNodeId(id * 100);
        workspace.markCreated();
        return workspace;
    }

    private static WorkspaceDocument document(Long id, Long workspaceId, Long ownerUserId) {
        WorkspaceDocument document = new WorkspaceDocument();
        document.setId(id);
        document.setOwnerUserId(ownerUserId);
        document.setOriginWorkspaceId(workspaceId);
        document.setTitle("Document " + id);
        document.markCreated();
        return document;
    }

    private static AuthContextProvider authContext(Long userId) {
        AuthSubject subject = new AuthSubject();
        subject.setUserId(userId);
        subject.setDisplayName("User " + userId);
        return () -> Optional.of(subject);
    }

    private static String userPrompt(ChatRequest request) {
        return request.getMessages().stream()
                .filter(message -> "user".equals(message.getRole()))
                .findFirst()
                .map(ChatMessage::getContent)
                .map(String::valueOf)
                .orElse("");
    }

    private static class CapturingUserFilesystemService extends UserFilesystemService {

        private UserFilesystemRetrieveCommand command;
        private UserFilesystemRetrieveResponse response = new UserFilesystemRetrieveResponse(
                List.of(),
                false,
                null,
                1,
                List.of()
        );
        private RuntimeException failure;

        CapturingUserFilesystemService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public UserFilesystemRetrieveResponse retrieve(UserFilesystemRetrieveCommand command) {
            this.command = command;
            if (failure != null) {
                throw failure;
            }
            return response;
        }
    }

    private static class CapturingChatModel implements AiChatModel {

        private ChatRequest request;
        private String modelName = "test-model";
        private String responseText = """
                {"candidates":[{"markdown":" first"},{"markdown":" second"}]}
                """;

        @Override
        public String id() {
            return "inline-test";
        }

        @Override
        public AiModelMetadata metadata() {
            return AiModelMetadata.builder()
                    .id(id())
                    .provider("openai-compatible")
                    .modelName(modelName)
                    .build();
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            this.request = request;
            ChatChoice choice = new ChatChoice();
            choice.setMessage(new ChatMessage("assistant", responseText));
            ChatResponse response = new ChatResponse();
            response.setChoices(List.of(choice));
            return response;
        }

        @Override
        public Flux<ChatStreamEvent> stream(ChatRequest request) {
            this.request = request;
            ChatDelta delta = new ChatDelta();
            delta.setContent("completion");
            ChatStreamEvent event = new ChatStreamEvent();
            event.setType(ChatStreamEventType.MESSAGE_DELTA);
            event.setDelta(delta);
            return Flux.just(event);
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
                    .toList();
        }
    }

    private static class InMemoryWorkspaceDocumentRepository implements WorkspaceDocumentRepository {

        private final Map<Long, WorkspaceDocument> documents = new LinkedHashMap<>();

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

    private static class InMemoryUserSettingRepository implements UserSettingRepository {

        private final Map<Long, Map<String, String>> valuesByUserId = new LinkedHashMap<>();

        @Override
        public List<UserSettingRecord> findByUserIdAndKeys(Long userId, Collection<String> keys) {
            Map<String, String> values = valuesByUserId.getOrDefault(userId, Map.of());
            return keys.stream()
                    .filter(values::containsKey)
                    .map(key -> new UserSettingRecord(userId, key, values.get(key)))
                    .toList();
        }

        @Override
        public void saveAll(Long userId, Map<String, String> settingValueByKey) {
            valuesByUserId.computeIfAbsent(userId, ignored -> new LinkedHashMap<>()).putAll(settingValueByKey);
        }

        @Override
        public void removeByUserIdAndKeys(Long userId, Collection<String> keys) {
            Map<String, String> values = valuesByUserId.get(userId);
            if (values != null) {
                keys.forEach(values::remove);
            }
        }

    }

}
