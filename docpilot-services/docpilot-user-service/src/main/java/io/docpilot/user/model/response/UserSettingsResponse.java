package io.docpilot.user.model.response;

import java.util.List;

/**
 * Effective user settings response.
 *
 * @param settings effective settings in definition order or requested key order.
 */
public record UserSettingsResponse(List<UserSettingResponse> settings) {
}
