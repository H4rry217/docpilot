package io.docpilot.user.repository;

import java.util.Map;
import java.util.Optional;

/**
 * Read-through cache boundary for one user's persisted setting values.
 *
 * <p>Cached values are raw strings from persistence. Missing cache means "load from repository",
 * while an empty cached map means "this user has no saved overrides".</p>
 */
public interface UserSettingCache {

    Optional<Map<String, String>> findByUserId(Long userId);

    void save(Long userId, Map<String, String> settingValueByKey);

    void evict(Long userId);
}
