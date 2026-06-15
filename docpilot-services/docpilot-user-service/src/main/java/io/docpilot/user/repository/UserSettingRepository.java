package io.docpilot.user.repository;

import io.docpilot.user.model.UserSettingRecord;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Storage boundary for user-owned settings.
 */
public interface UserSettingRepository {

    /**
     * Reads saved settings for one user and a fixed set of keys.
     *
     * @param userId owner user id.
     * @param keys setting keys to read.
     * @return matching persisted setting records.
     */
    List<UserSettingRecord> findByUserIdAndKeys(Long userId, Collection<String> keys);

    /**
     * Upserts saved settings for one user.
     *
     * @param userId owner user id.
     * @param settingValueByKey raw setting values keyed by setting key.
     */
    void saveAll(Long userId, Map<String, String> settingValueByKey);

    /**
     * Removes saved settings so effective values fall back to defaults.
     *
     * @param userId owner user id.
     * @param keys setting keys to remove.
     */
    void removeByUserIdAndKeys(Long userId, Collection<String> keys);

}
