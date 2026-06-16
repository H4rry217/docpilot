package io.docpilot.infrastructure.auth.provider;

/**
 * Feature flags exposed through /auth/config and enforced by AuthController.
 */
public record AuthProviderCapabilities(
        boolean supportsPasswordLogin,
        boolean supportsRegistration,
        boolean supportsPasswordChange,
        boolean supportsDisplayNameChange,
        boolean supportsHostToken
) {

    public static AuthProviderCapabilities localPassword(boolean supportsRegistration) {
        return new AuthProviderCapabilities(true, supportsRegistration, true, true, false);
    }

    public static AuthProviderCapabilities hostToken() {
        return new AuthProviderCapabilities(false, false, false, false, true);
    }

}
