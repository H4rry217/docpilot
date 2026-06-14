package io.docpilot.workspace.knowledge.queue;

import io.docpilot.workspace.knowledge.KnowledgeIndexCommandHandler;
import io.docpilot.workspace.knowledge.config.KnowledgeProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RedisKnowledgeIndexQueueTest {

    @Test
    void coalescesHighFrequencySavesForLatestRevision() {
        KnowledgeProperties properties = properties();
        InMemoryJobStore store = new InMemoryJobStore();
        MutableClock clock = new MutableClock();
        RedisKnowledgeIndexQueue queue = new RedisKnowledgeIndexQueue(
                properties,
                store,
                new InMemoryJobLock(),
                new CapturingHandler(),
                clock
        );

        queue.enqueueDocumentRevision(30L, 100L);
        clock.setMillis(500);
        queue.enqueueDocumentRevision(30L, 101L);
        clock.setMillis(600);
        queue.enqueueDocumentRevision(30L, 99L);

        KnowledgeIndexJob job = store.findJob(30L);
        assertThat(job.revisionId()).isEqualTo(101L);
        assertThat(job.firstQueuedAt()).isZero();
        assertThat(job.updatedAt()).isEqualTo(500);
        assertThat(store.dueAt(30L)).isEqualTo(3500);
    }

    @Test
    void capsDueTimeByMaxDelay() {
        KnowledgeProperties properties = properties();
        InMemoryJobStore store = new InMemoryJobStore();
        MutableClock clock = new MutableClock();
        RedisKnowledgeIndexQueue queue = new RedisKnowledgeIndexQueue(
                properties,
                store,
                new InMemoryJobLock(),
                new CapturingHandler(),
                clock
        );

        queue.enqueueDocumentRevision(30L, 100L);
        clock.setMillis(29000);
        queue.enqueueDocumentRevision(30L, 101L);

        assertThat(store.findJob(30L).revisionId()).isEqualTo(101L);
        assertThat(store.dueAt(30L)).isEqualTo(30000);
    }

    @Test
    void workerProcessesDueJobAndClearsIt() {
        KnowledgeProperties properties = properties();
        InMemoryJobStore store = new InMemoryJobStore();
        MutableClock clock = new MutableClock();
        CapturingHandler handler = new CapturingHandler();
        RedisKnowledgeIndexQueue queue = new RedisKnowledgeIndexQueue(properties, store, new InMemoryJobLock(), handler, clock);

        queue.enqueueDocumentRevision(30L, 100L);
        clock.setMillis(3000);
        queue.processDueJobs();

        assertThat(handler.indexed).containsExactly(new IndexCall(30L, 100L));
        assertThat(store.findJob(30L)).isNull();
        assertThat(store.dueAt(30L)).isNull();
    }

    @Test
    void workerKeepsNewerRevisionQueuedWhenSaveArrivesDuringIndexing() {
        KnowledgeProperties properties = properties();
        InMemoryJobStore store = new InMemoryJobStore();
        MutableClock clock = new MutableClock();
        CapturingHandler handler = new CapturingHandler();
        RedisKnowledgeIndexQueue queue = new RedisKnowledgeIndexQueue(properties, store, new InMemoryJobLock(), handler, clock);

        queue.enqueueDocumentRevision(30L, 100L);
        handler.onIndex = () -> {
            clock.setMillis(1000);
            queue.enqueueDocumentRevision(30L, 101L);
        };

        clock.setMillis(3000);
        queue.processDueJobs();

        assertThat(handler.indexed).containsExactly(new IndexCall(30L, 100L));
        assertThat(store.findJob(30L).revisionId()).isEqualTo(101L);
        assertThat(store.dueAt(30L)).isEqualTo(4000);
    }

    private KnowledgeProperties properties() {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.setEnabled(true);
        KnowledgeProperties.IndexProperties index = properties.getIndex();
        index.setDebounceDelayMs(3000);
        index.setMaxDelayMs(30000);
        index.setWorkerBatchSize(20);
        index.setLockTtlMs(1800000);
        index.setRetryDelayMs(10000);
        index.setJobTtlMs(86400000);
        return properties;
    }

    private record IndexCall(Long documentId, Long revisionId) {
    }

    private static class CapturingHandler extends KnowledgeIndexCommandHandler {

        private final List<IndexCall> indexed = new ArrayList<>();

        private Runnable onIndex;

        private CapturingHandler() {
            super(new KnowledgeProperties(), null, null, null, null, null, null);
        }

        @Override
        public void indexDocumentRevision(Long documentId, Long revisionId) {
            indexed.add(new IndexCall(documentId, revisionId));
            if (onIndex != null) {
                onIndex.run();
            }
        }
    }

    private static class InMemoryJobStore implements KnowledgeIndexJobStore {

        private final Map<Long, JobState> jobs = new LinkedHashMap<>();

        private final Map<Long, Long> dueTimes = new LinkedHashMap<>();

        @Override
        public long enqueue(Long documentId,
                            Long revisionId,
                            long nowMillis,
                            long debounceDelayMs,
                            long maxDelayMs,
                            long jobTtlMs) {
            JobState existing = jobs.get(documentId);
            if (existing != null && existing.revisionId > revisionId) {
                return dueTimes.getOrDefault(documentId, -1L);
            }

            long firstQueuedAt = existing == null ? nowMillis : existing.firstQueuedAt;
            long dueAt = nowMillis + Math.max(0, debounceDelayMs);
            if (maxDelayMs > 0) {
                dueAt = Math.min(dueAt, firstQueuedAt + maxDelayMs);
            }
            jobs.put(documentId, new JobState(documentId, revisionId, firstQueuedAt, nowMillis, 0));
            dueTimes.put(documentId, dueAt);
            return dueAt;
        }

        @Override
        public List<Long> dueDocumentIds(long nowMillis, int batchSize) {
            return dueTimes.entrySet().stream()
                    .filter(entry -> entry.getValue() <= nowMillis)
                    .sorted(Comparator.comparingLong(entry -> entry.getValue()))
                    .limit(batchSize)
                    .map(Map.Entry::getKey)
                    .toList();
        }

        @Override
        public KnowledgeIndexJob findJob(Long documentId) {
            JobState state = jobs.get(documentId);
            if (state == null) {
                return null;
            }
            return new KnowledgeIndexJob(
                    state.documentId,
                    state.revisionId,
                    state.firstQueuedAt,
                    state.updatedAt,
                    state.attempts
            );
        }

        @Override
        public boolean completeIfRevisionUnchanged(Long documentId, Long revisionId) {
            JobState state = jobs.get(documentId);
            if (state == null || !revisionId.equals(state.revisionId)) {
                return false;
            }
            jobs.remove(documentId);
            dueTimes.remove(documentId);
            return true;
        }

        @Override
        public long retryIfRevisionUnchanged(Long documentId,
                                             Long revisionId,
                                             long nowMillis,
                                             long retryAtMillis,
                                             long jobTtlMs) {
            JobState state = jobs.get(documentId);
            if (state == null || !revisionId.equals(state.revisionId)) {
                return -1;
            }
            state.attempts++;
            state.updatedAt = nowMillis;
            dueTimes.put(documentId, retryAtMillis);
            return state.attempts;
        }

        @Override
        public void cancel(Long documentId) {
            jobs.remove(documentId);
            dueTimes.remove(documentId);
        }

        private Long dueAt(Long documentId) {
            return dueTimes.get(documentId);
        }
    }

    private static class InMemoryJobLock implements KnowledgeIndexJobLock {

        private final List<Long> lockedDocumentIds = new ArrayList<>();

        @Override
        public boolean tryLock(Long documentId, long leaseTimeMs) {
            if (lockedDocumentIds.contains(documentId)) {
                return false;
            }
            lockedDocumentIds.add(documentId);
            return true;
        }

        @Override
        public void unlock(Long documentId) {
            lockedDocumentIds.remove(documentId);
        }
    }

    private static class JobState {

        private final Long documentId;

        private final Long revisionId;

        private final long firstQueuedAt;

        private long updatedAt;

        private long attempts;

        private JobState(Long documentId, Long revisionId, long firstQueuedAt, long updatedAt, long attempts) {
            this.documentId = documentId;
            this.revisionId = revisionId;
            this.firstQueuedAt = firstQueuedAt;
            this.updatedAt = updatedAt;
            this.attempts = attempts;
        }
    }

    private static class MutableClock extends Clock {

        private Instant instant = Instant.ofEpochMilli(0);

        private ZoneId zone = ZoneOffset.UTC;

        private void setMillis(long millis) {
            this.instant = Instant.ofEpochMilli(millis);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            MutableClock clock = new MutableClock();
            clock.instant = instant;
            clock.zone = zone;
            return clock;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

}
