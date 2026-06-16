package io.docpilot.infrastructure.auth.provider;

import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * Provider-neutral user identity returned after request authentication.
 */
public record AuthPrincipal(
        String providerId,
        String subject,
        String email,
        String displayName,
        Set<String> roles
) {

    public AuthPrincipal {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    /**
     * Creates a provider-neutral principal. The {@code subject} must be stable
     * and unique within the provider.
     */
    public static AuthPrincipal of(String providerId, String subject, String email, String displayName, Set<String> roles) {
        return new AuthPrincipal(providerId, subject, email, displayName, roles);
    }

    public AuthPrincipal withProviderId(String fallbackProviderId) {
        if (StringUtils.hasText(providerId)) {
            return this;
        }
        return new AuthPrincipal(fallbackProviderId, subject, email, displayName, roles);
    }

}
