package io.docpilot.infrastructure.auth.provider;

import io.docpilot.common.auth.AuthSubject;
import io.docpilot.common.context.RequestConstants;
import io.docpilot.common.exception.UnauthorizedException;
import io.docpilot.common.web.auth.AuthSubjectResolver;
import io.docpilot.infrastructure.auth.AuthIdentityService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * Bridges the web auth interceptor to the configured AuthProvider.
 */
public class ProviderAuthSubjectResolver implements AuthSubjectResolver {

    private final AuthProviderRegistry providerRegistry;
    private final AuthIdentityService identityService;

    public ProviderAuthSubjectResolver(AuthProviderRegistry providerRegistry,
                                       AuthIdentityService identityService) {
        this.providerRegistry = providerRegistry;
        this.identityService = identityService;
    }

    @Override
    public Optional<AuthSubject> resolve(HttpServletRequest request) {
        String token = bearerToken(request);
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }

        AuthProvider provider = providerRegistry.currentProvider();
        AuthRequest authRequest = new AuthRequest(token, request);
        // Providers authenticate their own token format; DocPilot normalizes the
        // returned principal and maps it to the AuthSubject used by business code.
        return provider.authenticateRequest(authRequest)
                .map(principal -> normalizePrincipal(provider, principal))
                .map(principal -> identityService.resolve(provider, principal));
    }

    private AuthPrincipal normalizePrincipal(AuthProvider provider, AuthPrincipal principal) {
        AuthPrincipal normalized = principal.withProviderId(provider.providerId());
        if (!provider.providerId().equals(normalized.providerId())) {
            throw new UnauthorizedException("Authentication provider mismatch");
        }
        return normalized;
    }

    private String bearerToken(HttpServletRequest request) {
        String authorization = request.getHeader(RequestConstants.HEADER_AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(RequestConstants.BEARER_PREFIX)) {
            return null;
        }
        String token = authorization.substring(RequestConstants.BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(token)) {
            throw new UnauthorizedException("Bearer token is empty");
        }
        return token;
    }

}
