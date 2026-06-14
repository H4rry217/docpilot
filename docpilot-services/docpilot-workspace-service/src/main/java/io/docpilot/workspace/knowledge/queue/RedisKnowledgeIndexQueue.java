package io.docpilot.workspace.knowledge.queue;

import io.docpilot.workspace.knowledge.KnowledgeIndexCommandHandler;
import io.docpilot.workspace.knowledge.config.KnowledgeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.util.List;

/**
 * Redis-backed debounced knowledge indexing queue and worker.
 *
 * <p>Flow: document saves enqueue the latest revision with a debounce window;
 * the scheduled worker polls due document ids, claims a short Redisson lock, then
 * indexes the revision stored in the job hash. Completion and retry both check
 * the revision id again so a newer save that arrives during indexing remains
 * queued instead of being cleared by the older worker run.
 */
public class RedisKnowledgeIndexQueue implements KnowledgeIndexQueue {

    private static final Logger log = LoggerFactory.getLogger(RedisKnowledgeIndexQueue.class);

    private final KnowledgeProperties properties;

    private final KnowledgeIndexJobStore jobStore;

    private final KnowledgeIndexJobLock jobLock;

    private final KnowledgeIndexCommandHandler handler;

    private final Clock clock;

    public RedisKnowledgeIndexQueue(KnowledgeProperties properties,
                                     KnowledgeIndexJobStore jobStore,
                                     KnowledgeIndexJobLock jobLock,
                                     KnowledgeIndexCommandHandler handler,
                                     Clock clock) {
        this.properties = properties;
        this.jobStore = jobStore;
        this.jobLock = jobLock;
        this.handler = handler;
        this.clock = clock;
    }

    @Override
    public void enqueueDocumentRevision(Long documentId, Long revisionId) {
        if (!properties.isEnabled()) {
            log.info("knowledge index enqueue skipped reason=disabled mode=redis documentId={} revisionId={}",
                    documentId, revisionId);
            return;
        }
        if (documentId == null || revisionId == null) {
            log.warn("knowledge index enqueue skipped reason=invalid_event mode=redis documentId={} revisionId={}",
                    documentId, revisionId);
            return;
        }

        long nowMillis = clock.millis();
        KnowledgeProperties.IndexProperties index = indexProperties();
        long dueAtMillis = jobStore.enqueue(
                documentId,
                revisionId,
                nowMillis,
                Math.max(0, index.getDebounceDelayMs()),
                index.getMaxDelayMs(),
                Math.max(1, index.getJobTtlMs())
        );
        log.info("knowledge index enqueued mode=redis documentId={} revisionId={} dueAtMillis={} debounceDelayMs={} maxDelayMs={}",
                documentId, revisionId, dueAtMillis, index.getDebounceDelayMs(), index.getMaxDelayMs());
    }

    @Override
    public void cancelDocument(Long documentId) {
        if (!properties.isEnabled()) {
            log.info("knowledge index queue cancel skipped reason=disabled mode=redis documentId={}", documentId);
            return;
        }
        if (documentId == null) {
            log.warn("knowledge index queue cancel skipped reason=invalid_document mode=redis documentId={}", documentId);
            return;
        }
        jobStore.cancel(documentId);
        log.info("knowledge index queue canceled mode=redis documentId={}", documentId);
    }

    @Scheduled(fixedDelayString = "${docpilot.knowledge.index.poll-interval-ms:1000}")
    public void processDueJobs() {
        if (!properties.isEnabled()) {
            return;
        }

        KnowledgeProperties.IndexProperties index = indexProperties();
        long nowMillis = clock.millis();
        List<Long> documentIds = jobStore.dueDocumentIds(nowMillis, Math.max(1, index.getWorkerBatchSize()));
        if (documentIds.isEmpty()) {
            return;
        }

        log.info("knowledge index worker tick dueJobs={} batchSize={}", documentIds.size(), index.getWorkerBatchSize());
        for (Long documentId : documentIds) {
            try {
                processDocument(documentId, index);
            } catch (RuntimeException exception) {
                log.error("knowledge index worker unexpected failure documentId={}", documentId, exception);
            }
        }
    }

    private void processDocument(Long documentId, KnowledgeProperties.IndexProperties index) {
        if (!jobLock.tryLock(documentId, Math.max(1, index.getLockTtlMs()))) {
            log.debug("knowledge index worker skipped reason=locked documentId={}", documentId);
            return;
        }

        try {
            KnowledgeIndexJob job = jobStore.findJob(documentId);
            if (job == null || job.revisionId() == null) {
                jobStore.cancel(documentId);
                log.info("knowledge index worker discarded reason=missing_job documentId={}", documentId);
                return;
            }

            log.info("knowledge index worker start documentId={} revisionId={} attempts={}",
                    job.documentId(), job.revisionId(), job.attempts());
            try {
                handler.indexDocumentRevision(job.documentId(), job.revisionId());
                // Another save may have queued a newer revision while indexing ran.
                // The Redis script only clears the job when the revision still matches.
                boolean completed = jobStore.completeIfRevisionUnchanged(job.documentId(), job.revisionId());
                log.info("knowledge index worker done documentId={} revisionId={} cleared={}",
                        job.documentId(), job.revisionId(), completed);
            } catch (RuntimeException exception) {
                long retryAtMillis = clock.millis() + Math.max(0, index.getRetryDelayMs());
                long attempts = jobStore.retryIfRevisionUnchanged(
                        job.documentId(),
                        job.revisionId(),
                        clock.millis(),
                        retryAtMillis,
                        Math.max(1, index.getJobTtlMs())
                );
                log.error("knowledge index worker failed documentId={} revisionId={} retryAtMillis={} attempts={}",
                        job.documentId(), job.revisionId(), retryAtMillis, attempts, exception);
            }
        } finally {
            jobLock.unlock(documentId);
        }
    }

    private KnowledgeProperties.IndexProperties indexProperties() {
        KnowledgeProperties.IndexProperties index = properties.getIndex();
        return index == null ? new KnowledgeProperties.IndexProperties() : index;
    }

}
