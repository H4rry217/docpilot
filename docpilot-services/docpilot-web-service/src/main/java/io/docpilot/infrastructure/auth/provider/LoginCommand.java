package io.docpilot.infrastructure.auth.provider;

import io.docpilot.common.web.logging.LogMask;

public record LoginCommand(String email, @LogMask String password) {
}
