package io.docpilot.infrastructure.auth.provider;

import io.docpilot.auth.AuthPrincipal;
import io.docpilot.auth.AuthProvider;
import io.docpilot.auth.AuthRequest;
import io.docpilot.common.auth.AuthSubject;
import io.docpilot.common.exception.UnauthorizedException;
import io.docpilot.common.web.auth.AuthSubjectResolver;
import io.docpilot.infrastructure.auth.AuthIdentityService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Bridges the web auth interceptor to the configured AuthProvider.
 */
public class ProviderAuthSubjectResolver implements AuthSubjectResolver {

    private static final Logger log = LoggerFactory.getLogger(ProviderAuthSubjectResolver.class);

    private final AuthProviderRegistry providerRegistry;
    private final AuthIdentityService identityService;
    private final ServletAuthRequestAdapter requestAdapter;

    public ProviderAuthSubjectResolver(AuthProviderRegistry providerRegistry,
                                       AuthIdentityService identityService) {
        this(providerRegistry, identityService, new ServletAuthRequestAdapter());
    }

    public ProviderAuthSubjectResolver(AuthProviderRegistry providerRegistry,
                                       AuthIdentityService identityService,
                                       ServletAuthRequestAdapter requestAdapter) {
        this.providerRegistry = providerRegistry;
        this.identityService = identityService;
        this.requestAdapter = requestAdapter;
    }

    @Override
    public Optional<AuthSubject> resolve(HttpServletRequest request) {
        AuthProvider provider = providerRegistry.currentProvider();
        AuthRequest authRequest = requestAdapter.adapt(request);
        // Providers authenticate their own token format; DocPilot normalizes the
        // returned principal and maps it to the AuthSubject used by business code.
        Optional<AuthPrincipal> principal = provider.authenticate(authRequest)
                .map(authPrincipal -> normalizePrincipal(provider, authPrincipal));
        if (principal.isEmpty()) {
            log.debug("auth subject unresolved providerId={} method={} path={}",
                    provider.providerId(), authRequest.method(), authRequest.path());
            return Optional.empty();
        }
        AuthSubject subject = identityService.resolve(provider, principal.get());
        log.debug("auth subject resolved providerId={} userId={} method={} path={}",
                provider.providerId(), subject.getUserId(), authRequest.method(), authRequest.path());
        return Optional.of(subject);
    }

    private AuthPrincipal normalizePrincipal(AuthProvider provider, AuthPrincipal principal) {
        AuthPrincipal normalized = principal.withProviderId(provider.providerId());
        if (!provider.providerId().equals(normalized.providerId())) {
            throw new UnauthorizedException("Authentication provider mismatch");
        }
        return normalized;
    }

}
