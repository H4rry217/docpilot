package io.docpilot.infrastructure.auth.provider;

import io.docpilot.common.web.logging.LogMask;
import io.docpilot.user.model.UserInformation;

public record AuthSession(@LogMask String token, UserInformation user) {
}
