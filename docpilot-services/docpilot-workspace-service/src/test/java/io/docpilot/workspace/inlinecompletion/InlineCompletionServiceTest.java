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
import io.docpilot.workspace.filesystem.UserFilesystemFailureMode;
import io.docpilot.workspace.filesystem.UserFilesystemService;
import io.docpilot.workspace.model.entity.Workspace;
import io.docpilot.workspace.model.entity.WorkspaceDocument;
import io.docpilot.workspace.model.request.InlineCompletionRequest;
import io.docpilot.workspace.model.request.UserFilesystemRetrieveCommand;
import io.docpilot.workspace.model.response.UserFilesystemRetrieveResponse;
import io.docpilot.workspace.processing.WorkspaceIdCodec;
import io.docpilot.workspace.repository.WorkspaceDocumentRepository;
import io.docpilot.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

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
    private InlineCompletionService service;

    @BeforeEach
    void setUp() {
        workspaceRepository = new InMemoryWorkspaceRepository();
        documentRepository = new InMemoryWorkspaceDocumentRepository();
        filesystemService = new CapturingUserFilesystemService();
        chatModel = new CapturingChatModel();

        workspaceRepository.save(workspace(WORKSPACE_ID, USER_ID));
        documentRepository.save(document(DOCUMENT_ID, WORKSPACE_ID, USER_ID));

        service = new InlineCompletionService(
                workspaceRepository,
                documentRepository,
                authContext(USER_ID),
                new WorkspaceIdCodec(),
                filesystemService,
                new AiModelRegistry("inline-test", List.of(chatModel)),
                new InlineCompletionProperties()
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

        InlineCompletionStream stream = service.stream(request(
                "PARAGRAPH",
                "Write a summary for this paragraph",
                ""
        ));

        assertThat(stream.shape()).isEqualTo(InlineCompletionShape.SENTENCE);
        assertThat(filesystemService.command.getPath()).isEqualTo("/workspace/11");
        assertThat(filesystemService.command.getTopK()).isEqualTo(4);
        assertThat(filesystemService.command.getMaxCharsPerHit()).isEqualTo(500);
        assertThat(filesystemService.command.getFailureMode()).isEqualTo(UserFilesystemFailureMode.BEST_EFFORT);
        String prompt = userPrompt(chatModel.request);
        assertThat(prompt).contains("Useful external context");
        assertThat(prompt).doesNotContain("Stale current document");
    }

    @Test
    void retrievalFailureBecomesDiagnosticAndModelStillStreams() {
        filesystemService.failure = new IllegalStateException("retrieval down");

        InlineCompletionStream stream = service.stream(request("PARAGRAPH", "Continue this", ""));

        assertThat(stream.diagnostics()).hasSize(1);
        assertThat(stream.diagnostics().getFirst().code()).isEqualTo("RETRIEVAL_FAILED");
        assertThat(chatModel.request).isNotNull();
        assertThat(stream.events().blockFirst()).isNotNull();
    }

    @Test
    void codeBlockUsesCodeLineShapeAndTokenLimit() {
        InlineCompletionStream stream = service.stream(request("CODE_BLOCK", "const value = ", ""));

        assertThat(stream.shape()).isEqualTo(InlineCompletionShape.CODE_LINE);
        assertThat(chatModel.request.getMaxOutputTokens()).isEqualTo(96);
        assertThat(userPrompt(chatModel.request)).contains("CODE_LINE markdown");
    }

    @Test
    void qwenModelDisablesThinkingForLowLatencyInlineCompletion() {
        chatModel.modelName = "qwen3.6-plus";

        service.stream(request("PARAGRAPH", "Continue this paragraph now", ""));

        assertThat(chatModel.request.getOptions()).containsEntry("enable_thinking", false);
    }

    @Test
    void previewTextDecodesEscapedBlockquoteMarkersForInlineShapes() {
        assertThat(InlineCompletionService.previewText("&gt; &gt; &gt; nested quote", InlineCompletionShape.SENTENCE))
                .isEqualTo("nested quote");
    }

    private static InlineCompletionRequest request(String blockType, String beforeCursor, String afterCursor) {
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
                "test"
        );
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
            ChatChoice choice = new ChatChoice();
            choice.setMessage(new ChatMessage("assistant", "completion"));
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

}
