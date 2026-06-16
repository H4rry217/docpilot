package io.docpilot.infrastructure.auth.provider;

import io.docpilot.common.web.logging.LogMask;

public record ChangePasswordCommand(@LogMask String currentPassword, @LogMask String newPassword) {
}
