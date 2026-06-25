package io.docpilot.workspace.knowledge.event;

import io.docpilot.workspace.knowledge.KnowledgeIndexCommandHandler;
import io.docpilot.workspace.knowledge.queue.KnowledgeIndexQueue;
import io.docpilot.workspace.event.DocumentContentChangedEvent;
import io.docpilot.workspace.event.DocumentDeletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Transaction-aware bridge from Spring application events to knowledge indexing.
 */
@Component
public class KnowledgeIndexEventListener {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexEventListener.class);

    /**
     * Debounced queue invoked only after the surrounding document transaction commits.
     */
    private final KnowledgeIndexQueue queue;

    private final KnowledgeIndexCommandHandler handler;

    public KnowledgeIndexEventListener(KnowledgeIndexQueue queue, KnowledgeIndexCommandHandler handler) {
        this.queue = queue;
        this.handler = handler;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentContentChanged(DocumentContentChangedEvent event) {
        log.info("knowledge index event received documentId={} revisionId={}", event.documentId(), event.revisionId());
        try {
            queue.enqueueDocumentRevision(event.documentId(), event.revisionId());
        } catch (RuntimeException exception) {
            log.error("knowledge index enqueue failed documentId={} revisionId={}",
                    event.documentId(), event.revisionId(), exception);
            throw exception;
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentDeleted(DocumentDeletedEvent event) {
        log.info("knowledge delete event received workspaceId={} documentId={}", event.workspaceId(), event.documentId());
        RuntimeException cancelFailure = null;
        try {
            queue.cancelDocument(event.documentId());
        } catch (RuntimeException exception) {
            cancelFailure = exception;
            log.error("knowledge delete queue cancel failed workspaceId={} documentId={}",
                    event.workspaceId(), event.documentId(), exception);
        }

        try {
            handler.deleteDocumentChunks(event.workspaceId(), event.documentId());
        } catch (RuntimeException exception) {
            if (cancelFailure != null) {
                exception.addSuppressed(cancelFailure);
            }
            log.error("knowledge delete event failed workspaceId={} documentId={}",
                    event.workspaceId(), event.documentId(), exception);
            throw exception;
        }

        if (cancelFailure != null) {
            throw cancelFailure;
        }
    }

}
