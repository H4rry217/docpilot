package io.docpilot.user.model;

/**
 * Persisted user setting row represented at the user-service boundary.
 *
 * @param userId owner of the setting.
 * @param key stable setting key.
 * @param settingValue raw persisted setting value; not limited to JSON syntax.
 */
public record UserSettingRecord(
        Long userId,
        String key,
        String settingValue
) {
}
