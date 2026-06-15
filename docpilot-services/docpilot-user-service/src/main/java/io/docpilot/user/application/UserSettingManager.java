package io.docpilot.user.application;

import io.docpilot.common.exception.BadRequestException;
import io.docpilot.user.model.UserSettingKeys;
import io.docpilot.user.model.UserSettingRecord;
import io.docpilot.user.model.UserSettingSource;
import io.docpilot.user.model.response.UserSettingResponse;
import io.docpilot.user.model.response.UserSettingsResponse;
import io.docpilot.user.repository.UserSettingCache;
import io.docpilot.user.repository.UserSettingRepository;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Application boundary for user-owned settings.
 *
 * <p>Reads merge saved per-user values with defaults. Saves upsert raw string values after
 * whitelist validation, and removes delete saved rows so the effective value falls back to default.</p>
 */
public class UserSettingManager {

    /**
     * Persistence boundary for saved setting values.
     */
    private final UserSettingRepository userSettingRepository;

    /**
     * Optional read-through cache for one user's saved setting values.
     */
    private final UserSettingCache userSettingCache;

    public UserSettingManager(UserSettingRepository userSettingRepository) {
        this(userSettingRepository, null);
    }

    public UserSettingManager(UserSettingRepository userSettingRepository,
                              UserSettingCache userSettingCache) {
        this.userSettingRepository = userSettingRepository;
        this.userSettingCache = userSettingCache;
    }

    public UserSettingsResponse getSettings(Long userId, Collection<String> keys) {
        Long effectiveUserId = requireUserId(userId);
        List<String> effectiveKeys = effectiveKeys(keys);
        Map<String, String> savedValueByKey = savedValueByKey(effectiveUserId);
        List<UserSettingResponse> settings = effectiveKeys.stream()
                .map(key -> responseFor(key, savedValueByKey.get(key)))
                .toList();
        return new UserSettingsResponse(settings);
    }

    public UserSettingsResponse saveSettings(Long userId, Map<String, Object> values) {
        Long effectiveUserId = requireUserId(userId);
        if (values == null || values.isEmpty()) {
            return getSettings(effectiveUserId, List.of());
        }

        Map<String, String> settingValueByKey = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String key = UserSettingKeys.requireKey(entry.getKey());
            settingValueByKey.put(key, UserSettingKeys.serialize(key, entry.getValue()));
        }
        userSettingRepository.saveAll(effectiveUserId, settingValueByKey);
        if (userSettingCache != null) {
            userSettingCache.evict(effectiveUserId);
        }
        return getSettings(effectiveUserId, settingValueByKey.keySet());
    }

    public UserSettingsResponse removeSettings(Long userId, Collection<String> keys) {
        Long effectiveUserId = requireUserId(userId);
        if (keys == null || keys.isEmpty()) {
            return getSettings(effectiveUserId, List.of());
        }
        List<String> effectiveKeys = effectiveKeys(keys);
        userSettingRepository.removeByUserIdAndKeys(effectiveUserId, effectiveKeys);
        if (userSettingCache != null) {
            userSettingCache.evict(effectiveUserId);
        }
        return getSettings(effectiveUserId, effectiveKeys);
    }

    public Optional<Object> findUserValue(Long userId, String key) {
        Long effectiveUserId = requireUserId(userId);
        String effectiveKey = UserSettingKeys.requireKey(key);
        String settingValue = savedValueByKey(effectiveUserId).get(effectiveKey);
        if (settingValue == null) {
            return Optional.empty();
        }
        return Optional.of(storedValue(effectiveKey, settingValue));
    }

    private List<String> effectiveKeys(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return UserSettingKeys.allKeys();
        }
        LinkedHashSet<String> uniqueKeys = new LinkedHashSet<>();
        for (String key : keys) {
            uniqueKeys.add(UserSettingKeys.requireKey(key));
        }
        return List.copyOf(uniqueKeys);
    }

    private Map<String, String> savedValueByKey(Long userId) {
        if (userSettingCache != null) {
            Optional<Map<String, String>> cached = userSettingCache.findByUserId(userId);
            if (cached.isPresent()) {
                return cached.get();
            }
        }
        Map<String, String> savedValueByKey = new LinkedHashMap<>();
        for (UserSettingRecord record : userSettingRepository.findByUserIdAndKeys(userId, UserSettingKeys.allKeys())) {
            savedValueByKey.put(record.key(), record.settingValue());
        }
        if (userSettingCache != null) {
            userSettingCache.save(userId, savedValueByKey);
        }
        return savedValueByKey;
    }

    private UserSettingResponse responseFor(String key, String settingValue) {
        String effectiveKey = UserSettingKeys.requireKey(key);
        if (settingValue == null) {
            return new UserSettingResponse(
                    effectiveKey,
                    UserSettingKeys.defaultValue(effectiveKey),
                    UserSettingSource.DEFAULT,
                    UserSettingKeys.description(effectiveKey)
            );
        }
        return new UserSettingResponse(
                effectiveKey,
                storedValue(effectiveKey, settingValue),
                UserSettingSource.USER,
                UserSettingKeys.description(effectiveKey)
        );
    }

    private Object storedValue(String key, String settingValue) {
        try {
            return UserSettingKeys.parseStored(key, settingValue);
        } catch (RuntimeException exception) {
            // Corrupt stored values should not make the settings page unusable; fall back to the safe default.
            return UserSettingKeys.defaultValue(key);
        }
    }

    private Long requireUserId(Long userId) {
        if (userId == null) {
            throw new BadRequestException("userId is required");
        }
        return userId;
    }

}
