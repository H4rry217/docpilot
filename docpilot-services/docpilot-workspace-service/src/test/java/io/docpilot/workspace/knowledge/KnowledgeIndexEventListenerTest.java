package io.docpilot.workspace.knowledge;

import io.docpilot.workspace.knowledge.config.KnowledgeProperties;
import io.docpilot.workspace.knowledge.event.DocumentContentChangedEvent;
import io.docpilot.workspace.knowledge.event.DocumentKnowledgeDeletedEvent;
import io.docpilot.workspace.knowledge.event.KnowledgeIndexEventListener;
import io.docpilot.workspace.knowledge.queue.KnowledgeIndexQueue;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeIndexEventListenerTest {

    @Test
    void enqueuesDocumentContentChangedEvents() {
        CapturingQueue queue = new CapturingQueue();
        CapturingHandler handler = new CapturingHandler();
        KnowledgeIndexEventListener listener = new KnowledgeIndexEventListener(queue, handler);

        listener.onDocumentContentChanged(new DocumentContentChangedEvent(30L, 100L));

        assertThat(queue.enqueued).containsExactly(new IndexCall(30L, 100L));
        assertThat(handler.indexed).isEmpty();
    }

    @Test
    void cancelsQueueAndDeletesChunksWhenDocumentIsDeleted() {
        CapturingQueue queue = new CapturingQueue();
        CapturingHandler handler = new CapturingHandler();
        KnowledgeIndexEventListener listener = new KnowledgeIndexEventListener(queue, handler);

        listener.onDocumentKnowledgeDeleted(new DocumentKnowledgeDeletedEvent(20L, 30L));

        assertThat(queue.canceled).containsExactly(30L);
        assertThat(handler.deleted).containsExactly(new DeleteCall(20L, 30L));
    }

    private record IndexCall(Long documentId, Long revisionId) {
    }

    private record DeleteCall(Long workspaceId, Long documentId) {
    }

    private static class CapturingQueue implements KnowledgeIndexQueue {

        private final List<IndexCall> enqueued = new ArrayList<>();

        private final List<Long> canceled = new ArrayList<>();

        @Override
        public void enqueueDocumentRevision(Long documentId, Long revisionId) {
            enqueued.add(new IndexCall(documentId, revisionId));
        }

        @Override
        public void cancelDocument(Long documentId) {
            canceled.add(documentId);
        }
    }

    private static class CapturingHandler extends KnowledgeIndexCommandHandler {

        private final List<IndexCall> indexed = new ArrayList<>();

        private final List<DeleteCall> deleted = new ArrayList<>();

        private CapturingHandler() {
            super(new KnowledgeProperties(), null, null, null, null, null, null);
        }

        @Override
        public void indexDocumentRevision(Long documentId, Long revisionId) {
            indexed.add(new IndexCall(documentId, revisionId));
        }

        @Override
        public void deleteDocumentChunks(Long workspaceId, Long documentId) {
            deleted.add(new DeleteCall(workspaceId, documentId));
        }
    }

}
