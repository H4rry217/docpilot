package io.docpilot.infrastructure.auth.provider;

import io.docpilot.common.web.logging.LogMask;

public record RegisterCommand(String email, @LogMask String password, String displayName) {
}
