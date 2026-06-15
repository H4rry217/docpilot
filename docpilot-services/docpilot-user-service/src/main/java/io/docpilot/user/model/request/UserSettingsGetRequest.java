package io.docpilot.user.model.request;

import java.util.List;

/**
 * Request for reading effective user settings.
 *
 * @param keys optional setting keys; empty means every whitelisted key.
 */
public record UserSettingsGetRequest(List<String> keys) {
}
