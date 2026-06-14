package io.docpilot.workspace.knowledge;

import io.docpilot.ai.AiChatModel;
import io.docpilot.ai.AiModelMetadata;
import io.docpilot.ai.AiModelRegistry;
import io.docpilot.ai.model.ChatChoice;
import io.docpilot.ai.model.ChatMessage;
import io.docpilot.ai.model.ChatRequest;
import io.docpilot.ai.model.ChatResponse;
import io.docpilot.ai.model.ChatStreamEvent;
import io.docpilot.workspace.knowledge.config.KnowledgeProperties;
import io.docpilot.workspace.knowledge.model.KnowledgeChunkDraft;
import io.docpilot.workspace.knowledge.model.KnowledgeSectionDraft;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AiKnowledgeSummaryServiceTest {

    @Test
    void usesConfiguredSummaryPromptTemplates() {
        CapturingChatModel model = new CapturingChatModel();
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.setSummaryModelId("summary");
        properties.setSummaryMaxInputChars(16);
        properties.setSummaryMaxOutputTokens(64);
        properties.getPrompts().getSummary().setSystem("SYSTEM TEMPLATE");
        properties.getPrompts().getSummary().setUser("Path={{headingPath}}\nContent={{content}}");
        AiKnowledgeSummaryService service = new AiKnowledgeSummaryService(
                new AiModelRegistry("summary", List.of(model)),
                properties
        );

        KnowledgeSectionDraft section = new KnowledgeSectionDraft();
        section.setHeadingBlockId("h1");
        section.setHeadingPath(List.of("Guide", "Details"));
        section.setChunkIndex(2);
        section.setContent("0123456789abcdefghijklmnopqrstuvwxyz");

        List<KnowledgeChunkDraft> summaries = service.summarize(List.of(section));

        assertThat(model.request.getMessages().get(0).getContent()).isEqualTo("SYSTEM TEMPLATE");
        assertThat(model.request.getMessages().get(1).getContent())
                .isEqualTo("Path=Guide / Details\nContent=0123456789abcdef");
        assertThat(summaries).hasSize(1);
        assertThat(summaries.getFirst().getChunkIndex()).isEqualTo(2);
        assertThat(summaries.getFirst().getContent()).isEqualTo("configured summary");
    }

    @Test
    void summarizesSectionsConcurrentlyAndPreservesInputOrder() {
        ConcurrentChatModel model = new ConcurrentChatModel();
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.setSummaryModelId("summary");
        properties.setSummaryConcurrency(2);
        properties.getPrompts().getSummary().setSystem("SYSTEM");
        properties.getPrompts().getSummary().setUser("{{content}}");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            AiKnowledgeSummaryService service = new AiKnowledgeSummaryService(
                    new AiModelRegistry("summary", List.of(model)),
                    properties,
                    executor
            );

            List<KnowledgeChunkDraft> summaries = service.summarize(List.of(
                    section("h1", 0, "A"),
                    section("h2", 1, "B"),
                    section("h3", 2, "C")
            ));

            assertThat(model.maxInFlight.get()).isEqualTo(2);
            assertThat(summaries)
                    .extracting(KnowledgeChunkDraft::getContent)
                    .containsExactly("summary:A", "summary:B", "summary:C");
        } finally {
            executor.shutdownNow();
        }
    }

    private static KnowledgeSectionDraft section(String headingBlockId, int chunkIndex, String content) {
        KnowledgeSectionDraft section = new KnowledgeSectionDraft();
        section.setHeadingBlockId(headingBlockId);
        section.setHeadingPath(List.of("Guide", headingBlockId));
        section.setChunkIndex(chunkIndex);
        section.setContent(content);
        return section;
    }

    private static class CapturingChatModel implements AiChatModel {

        private ChatRequest request;

        @Override
        public String id() {
            return "summary";
        }

        @Override
        public AiModelMetadata metadata() {
            return AiModelMetadata.of(id());
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            this.request = request;
            ChatChoice choice = new ChatChoice();
            choice.setMessage(new ChatMessage("assistant", "configured summary"));
            ChatResponse response = new ChatResponse();
            response.setChoices(List.of(choice));
            return response;
        }

        @Override
        public Flux<ChatStreamEvent> stream(ChatRequest request) {
            return Flux.empty();
        }

    }

    private static class ConcurrentChatModel implements AiChatModel {

        private final CountDownLatch firstTwoStarted = new CountDownLatch(2);

        private final AtomicInteger inFlight = new AtomicInteger();

        private final AtomicInteger maxInFlight = new AtomicInteger();

        @Override
        public String id() {
            return "summary";
        }

        @Override
        public AiModelMetadata metadata() {
            return AiModelMetadata.of(id());
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            String content = String.valueOf(request.getMessages().get(1).getContent());
            int currentInFlight = inFlight.incrementAndGet();
            maxInFlight.accumulateAndGet(currentInFlight, Math::max);
            firstTwoStarted.countDown();
            try {
                firstTwoStarted.await(1, TimeUnit.SECONDS);
                if ("A".equals(content)) {
                    TimeUnit.MILLISECONDS.sleep(50);
                }
                ChatChoice choice = new ChatChoice();
                choice.setMessage(new ChatMessage("assistant", "summary:" + content));
                ChatResponse response = new ChatResponse();
                response.setChoices(List.of(choice));
                return response;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("summary test interrupted", exception);
            } finally {
                inFlight.decrementAndGet();
            }
        }

        @Override
        public Flux<ChatStreamEvent> stream(ChatRequest request) {
            return Flux.empty();
        }

    }

}
