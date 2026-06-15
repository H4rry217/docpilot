package io.docpilot.user.model.request;

import java.util.Map;

/**
 * Request for saving user settings.
 *
 * @param values typed values keyed by setting key.
 */
public record UserSettingsSaveRequest(Map<String, Object> values) {
}
