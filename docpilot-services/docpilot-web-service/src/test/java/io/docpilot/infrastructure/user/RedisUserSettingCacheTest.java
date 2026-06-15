package io.docpilot.infrastructure.user;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisUserSettingCacheTest {

    @Test
    void readsAndWritesUserSettingHashWithMarkerAndTtl() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries("docpilot:user-settings:7:v1")).thenReturn(new LinkedHashMap<>(Map.of(
                "_cached", "1",
                "app.locale", "en-US",
                "inlineCompletion.maxOutputTokens.short", "64"
        )));
        RedisUserSettingCache cache = new RedisUserSettingCache(redisTemplate, Duration.ofMinutes(5));

        assertThat(cache.findByUserId(7L))
                .hasValueSatisfying(values -> assertThat(values)
                        .containsEntry("app.locale", "en-US")
                        .containsEntry("inlineCompletion.maxOutputTokens.short", "64")
                        .doesNotContainKey("_cached"));

        cache.save(7L, Map.of("app.locale", "zh-CN"));

        verify(redisTemplate).delete("docpilot:user-settings:7:v1");
        verify(hashOperations).putAll(eq("docpilot:user-settings:7:v1"), any(Map.class));
        verify(redisTemplate).expire("docpilot:user-settings:7:v1", Duration.ofMinutes(5));
    }

    @Test
    void redisFailuresAreBestEffort() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.opsForHash()).thenThrow(new IllegalStateException("redis down"));
        RedisUserSettingCache cache = new RedisUserSettingCache(redisTemplate, Duration.ofMinutes(5));

        assertThat(cache.findByUserId(7L)).isEmpty();
        assertThatNoException().isThrownBy(() -> cache.save(7L, Map.of("app.locale", "en-US")));
        assertThatNoException().isThrownBy(() -> cache.evict(7L));
    }
}
