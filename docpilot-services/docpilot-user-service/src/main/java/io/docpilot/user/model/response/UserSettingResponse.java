package io.docpilot.user.model.response;

import io.docpilot.user.model.UserSettingSource;

/**
 * Effective user setting returned to clients.
 *
 * @param key stable setting key.
 * @param value typed JSON value.
 * @param source whether the value came from user storage or defaults.
 * @param description human-readable setting description.
 */
public record UserSettingResponse(
        String key,
        Object value,
        UserSettingSource source,
        String description
) {
}
