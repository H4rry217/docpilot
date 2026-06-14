package io.docpilot.workspace.knowledge.queue;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Redis-backed store for debounced knowledge indexing jobs.
 *
 * <p>The queue stores one job hash per document and a global sorted set keyed by
 * next due time. Lua scripts keep each multi-key state transition atomic, so a
 * worker never removes a newer revision that was enqueued while an older one was
 * being indexed. Processing mutual exclusion is handled separately by
 * {@link KnowledgeIndexJobLock}.
 */
public class RedisKnowledgeIndexJobStore implements KnowledgeIndexJobStore {

    public static final String DUE_KEY = "docpilot:knowledge:index:due";

    private static final String SCRIPT_PATH_PREFIX = "redis/knowledge/";

    private static final String JOB_KEY_PREFIX = "docpilot:knowledge:index:job:";

    private static final String REVISION_ID_FIELD = "revisionId";

    private static final String FIRST_QUEUED_AT_FIELD = "firstQueuedAt";

    private static final String UPDATED_AT_FIELD = "updatedAt";

    private static final String ATTEMPTS_FIELD = "attempts";

    private static final DefaultRedisScript<Long> ENQUEUE_SCRIPT = script("enqueue-index-job.lua");

    private static final DefaultRedisScript<Long> COMPLETE_SCRIPT = script("complete-index-job.lua");

    private static final DefaultRedisScript<Long> RETRY_SCRIPT = script("retry-index-job.lua");

    private final StringRedisTemplate redisTemplate;

    public RedisKnowledgeIndexJobStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public long enqueue(Long documentId,
                        Long revisionId,
                        long nowMillis,
                        long debounceDelayMs,
                        long maxDelayMs,
                        long jobTtlMs) {
        // Enqueue is the only writer that coalesces document save bursts: it keeps
        // the latest revision, pushes the due score forward by debounce delay, and
        // caps the delay by the first queued time.
        Long result = redisTemplate.execute(
                ENQUEUE_SCRIPT,
                List.of(DUE_KEY, jobKey(documentId)),
                String.valueOf(revisionId),
                String.valueOf(nowMillis),
                String.valueOf(Math.max(0, debounceDelayMs)),
                String.valueOf(maxDelayMs),
                String.valueOf(Math.max(1, jobTtlMs)),
                String.valueOf(documentId)
        );
        return result == null ? -1 : result;
    }

    @Override
    public List<Long> dueDocumentIds(long nowMillis, int batchSize) {
        Set<String> members = redisTemplate.opsForZSet()
                .rangeByScore(DUE_KEY, 0, nowMillis, 0, Math.max(1, batchSize));
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        return members.stream()
                .map(this::parseLong)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public KnowledgeIndexJob findJob(Long documentId) {
        Map<Object, Object> values = redisTemplate.opsForHash().entries(jobKey(documentId));
        if (values == null || values.isEmpty()) {
            return null;
        }
        Long revisionId = longValue(values.get(REVISION_ID_FIELD));
        if (revisionId == null) {
            return null;
        }
        return new KnowledgeIndexJob(
                documentId,
                revisionId,
                longValueOrDefault(values.get(FIRST_QUEUED_AT_FIELD), 0),
                longValueOrDefault(values.get(UPDATED_AT_FIELD), 0),
                longValueOrDefault(values.get(ATTEMPTS_FIELD), 0)
        );
    }

    @Override
    public boolean completeIfRevisionUnchanged(Long documentId, Long revisionId) {
        Long result = redisTemplate.execute(
                COMPLETE_SCRIPT,
                List.of(jobKey(documentId), DUE_KEY),
                String.valueOf(revisionId),
                String.valueOf(documentId)
        );
        return result != null && result > 0;
    }

    @Override
    public long retryIfRevisionUnchanged(Long documentId,
                                         Long revisionId,
                                         long nowMillis,
                                         long retryAtMillis,
                                         long jobTtlMs) {
        Long result = redisTemplate.execute(
                RETRY_SCRIPT,
                List.of(jobKey(documentId), DUE_KEY),
                String.valueOf(revisionId),
                String.valueOf(nowMillis),
                String.valueOf(retryAtMillis),
                String.valueOf(Math.max(1, jobTtlMs)),
                String.valueOf(documentId)
        );
        return result == null ? -1 : result;
    }

    @Override
    public void cancel(Long documentId) {
        redisTemplate.delete(jobKey(documentId));
        redisTemplate.opsForZSet().remove(DUE_KEY, String.valueOf(documentId));
    }

    private static DefaultRedisScript<Long> script(String fileName) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setLocation(new ClassPathResource(SCRIPT_PATH_PREFIX + fileName));
        return script;
    }

    private String jobKey(Long documentId) {
        return JOB_KEY_PREFIX + documentId;
    }

    private Long parseLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        return parseLong(String.valueOf(value));
    }

    private long longValueOrDefault(Object value, long fallback) {
        Long parsed = longValue(value);
        return parsed == null ? fallback : parsed;
    }

}
