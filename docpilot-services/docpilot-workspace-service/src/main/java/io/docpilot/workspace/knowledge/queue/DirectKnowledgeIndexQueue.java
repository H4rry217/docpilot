package io.docpilot.workspace.knowledge.queue;

import io.docpilot.workspace.knowledge.KnowledgeIndexCommandHandler;
import io.docpilot.workspace.knowledge.config.KnowledgeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Immediate indexing queue used as a local fallback.
 */
public class DirectKnowledgeIndexQueue implements KnowledgeIndexQueue {

    private static final Logger log = LoggerFactory.getLogger(DirectKnowledgeIndexQueue.class);

    private final KnowledgeProperties properties;

    private final KnowledgeIndexCommandHandler handler;

    public DirectKnowledgeIndexQueue(KnowledgeProperties properties, KnowledgeIndexCommandHandler handler) {
        this.properties = properties;
        this.handler = handler;
    }

    @Override
    public void enqueueDocumentRevision(Long documentId, Long revisionId) {
        if (!properties.isEnabled()) {
            log.info("knowledge index enqueue skipped reason=disabled mode=direct documentId={} revisionId={}",
                    documentId, revisionId);
            return;
        }
        handler.indexDocumentRevision(documentId, revisionId);
    }

    @Override
    public void cancelDocument(Long documentId) {
        log.debug("knowledge index queue cancel ignored mode=direct documentId={}", documentId);
    }

}
