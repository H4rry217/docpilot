package io.docpilot.user.model.request;

import java.util.List;

/**
 * Request for removing saved user settings.
 *
 * @param keys setting keys to reset to defaults.
 */
public record UserSettingsRemoveRequest(List<String> keys) {
}
