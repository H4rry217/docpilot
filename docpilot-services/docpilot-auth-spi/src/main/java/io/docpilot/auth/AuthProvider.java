package io.docpilot.auth;

import java.util.Optional;

/**
 * Pluggable authentication boundary for DocPilot deployments.
 *
 * <p>Implementations own token validation and upstream identity lookup. DocPilot
 * core selects the configured provider, maps the returned principal to a
 * DocPilot user, and exposes a local {@code AuthSubject} to business code.</p>
 */
public interface AuthProvider {

    /**
     * Stable id used by {@code docpilot.auth.provider}.
     */
    String providerId();

    /**
     * Declares which generic /auth APIs and frontend flows this provider supports.
     */
    AuthProviderCapabilities capabilities();

    /**
     * Resolves a principal directly to an existing DocPilot user id when the
     * provider itself owns that relationship.
     *
     * <p>Most custom providers should keep this default and let DocPilot bind
     * {@code providerId + subject} through {@code docpilot_user_identity}.</p>
     */
    default Optional<Long> resolveUserId(AuthPrincipal principal) {
        return Optional.empty();
    }

    /**
     * Authenticates the current request.
     *
     * <p>Return {@link Optional#empty()} when the request has no authenticated
     * principal. Throw an auth exception when supplied credentials are malformed,
     * expired, or otherwise rejected.</p>
     */
    Optional<AuthPrincipal> authenticate(AuthRequest request);

}
