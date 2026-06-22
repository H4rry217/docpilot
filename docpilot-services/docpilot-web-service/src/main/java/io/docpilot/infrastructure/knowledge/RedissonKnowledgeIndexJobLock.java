package io.docpilot.infrastructure.knowledge;

import io.docpilot.workspace.knowledge.queue.KnowledgeIndexJobLock;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Redisson-backed document worker lock with consistent business logs.
 */
public class RedissonKnowledgeIndexJobLock implements KnowledgeIndexJobLock {

    private static final Logger log = LoggerFactory.getLogger(RedissonKnowledgeIndexJobLock.class);

    private static final String LOCK_KEY_PREFIX = "docpilot:knowledge:index:lock:";

    private final RedissonClient redissonClient;

    public RedissonKnowledgeIndexJobLock(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Override
    public boolean tryLock(Long documentId, long leaseTimeMs) {
        String key = lockKey(documentId);
        long effectiveLeaseTimeMs = Math.max(1, leaseTimeMs);
        RLock lock = redissonClient.getLock(key);
        try {
            boolean locked = lock.tryLock(0, effectiveLeaseTimeMs, TimeUnit.MILLISECONDS);
            if (locked) {
                log.info("knowledge index lock acquired provider=redisson documentId={} key={} leaseTimeMs={}",
                        documentId, key, effectiveLeaseTimeMs);
            } else {
                log.debug("knowledge index lock busy provider=redisson documentId={} key={} leaseTimeMs={}",
                        documentId, key, effectiveLeaseTimeMs);
            }
            return locked;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("knowledge index lock interrupted provider=redisson documentId={} key={} leaseTimeMs={}",
                    documentId, key, effectiveLeaseTimeMs, exception);
            return false;
        }
    }

    @Override
    public void unlock(Long documentId) {
        String key = lockKey(documentId);
        RLock lock = redissonClient.getLock(key);
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("knowledge index lock released provider=redisson documentId={} key={}", documentId, key);
            } else {
                log.warn("knowledge index lock release skipped reason=not_owner provider=redisson documentId={} key={}",
                        documentId, key);
            }
        } catch (RuntimeException exception) {
            log.warn("knowledge index lock release failed provider=redisson documentId={} key={}",
                    documentId, key, exception);
        }
    }

    private String lockKey(Long documentId) {
        return LOCK_KEY_PREFIX + documentId;
    }

}
