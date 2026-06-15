package io.docpilot.infrastructure.user;

import io.docpilot.user.repository.UserSettingCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Redis hash cache for all saved setting overrides of one user.
 */
public class RedisUserSettingCache implements UserSettingCache {

    private static final Logger log = LoggerFactory.getLogger(RedisUserSettingCache.class);

    private static final String KEY_PREFIX = "docpilot:user-settings:";

    private static final String KEY_SUFFIX = ":v1";

    /**
     * Marker lets Redis distinguish "cached but empty" from "cache miss".
     */
    private static final String CACHE_MARKER_FIELD = "_cached";

    private final StringRedisTemplate redisTemplate;

    private final Duration ttl;

    public RedisUserSettingCache(StringRedisTemplate redisTemplate, Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.ttl = ttl == null || ttl.isNegative() || ttl.isZero() ? Duration.ofMinutes(10) : ttl;
    }

    @Override
    public Optional<Map<String, String>> findByUserId(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        String key = key(userId);
        try {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
            if (entries.isEmpty() || !entries.containsKey(CACHE_MARKER_FIELD)) {
                log.debug("user setting cache miss userId={} key={}", userId, key);
                return Optional.empty();
            }
            Map<String, String> values = new LinkedHashMap<>();
            entries.forEach((field, value) -> {
                String settingKey = String.valueOf(field);
                if (!CACHE_MARKER_FIELD.equals(settingKey) && value != null) {
                    values.put(settingKey, String.valueOf(value));
                }
            });
            log.debug("user setting cache hit userId={} key={} values={}", userId, key, values.size());
            return Optional.of(values);
        } catch (RuntimeException exception) {
            log.debug("user setting cache read failed userId={} key={}", userId, key, exception);
            return Optional.empty();
        }
    }

    @Override
    public void save(Long userId, Map<String, String> settingValueByKey) {
        if (userId == null || settingValueByKey == null) {
            return;
        }
        String key = key(userId);
        try {
            Map<String, String> cacheValues = new LinkedHashMap<>(settingValueByKey);
            cacheValues.put(CACHE_MARKER_FIELD, "1");
            redisTemplate.delete(key);
            redisTemplate.opsForHash().putAll(key, cacheValues);
            redisTemplate.expire(key, ttl);
            log.debug("user setting cache saved userId={} key={} values={} ttlMs={}",
                    userId, key, settingValueByKey.size(), ttl.toMillis());
        } catch (RuntimeException exception) {
            log.debug("user setting cache save failed userId={} key={}", userId, key, exception);
        }
    }

    @Override
    public void evict(Long userId) {
        if (userId == null) {
            return;
        }
        String key = key(userId);
        try {
            redisTemplate.delete(key);
            log.debug("user setting cache evicted userId={} key={}", userId, key);
        } catch (RuntimeException exception) {
            log.debug("user setting cache evict failed userId={} key={}", userId, key, exception);
        }
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId + KEY_SUFFIX;
    }
}
