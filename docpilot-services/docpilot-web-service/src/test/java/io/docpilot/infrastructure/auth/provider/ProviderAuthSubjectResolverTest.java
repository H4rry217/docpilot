package io.docpilot.infrastructure.auth.provider;

import io.docpilot.auth.AuthLoginFlow;
import io.docpilot.auth.AuthPrincipal;
import io.docpilot.auth.AuthProvider;
import io.docpilot.auth.AuthProviderCapabilities;
import io.docpilot.auth.AuthRequest;
import io.docpilot.common.auth.AuthSubject;
import io.docpilot.infrastructure.auth.AuthIdentityService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProviderAuthSubjectResolverTest {

    @Test
    void callsProviderWhenRequestHasNoBearerToken() {
        AuthProviderProperties properties = new AuthProviderProperties();
        properties.setProvider("remote-user");
        AuthPrincipal principal = AuthPrincipal.of("remote-user", "alice", "alice@example.com", "Alice", Set.of());
        AuthProvider provider = new AuthProvider() {
            @Override
            public String providerId() {
                return "remote-user";
            }

            @Override
            public AuthProviderCapabilities capabilities() {
                return new AuthProviderCapabilities(Set.of(AuthLoginFlow.REMOTE_USER), Set.of());
            }

            @Override
            public Optional<AuthPrincipal> authenticate(AuthRequest request) {
                assertThat(request.bearerToken()).isEmpty();
                assertThat(request.firstHeader("X-Remote-User")).contains("alice");
                assertThat(request.path()).isEqualTo("/auth/me");
                return Optional.of(principal);
            }
        };
        AuthProviderRegistry registry = new AuthProviderRegistry(properties, List.of(provider));
        AuthIdentityService identityService = mock(AuthIdentityService.class);
        AuthSubject subject = new AuthSubject();
        subject.setUserId(42L);
        when(identityService.resolve(eq(provider), eq(principal))).thenReturn(subject);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/me");
        request.addHeader("X-Remote-User", "alice");

        Optional<AuthSubject> resolved = new ProviderAuthSubjectResolver(registry, identityService).resolve(request);

        assertThat(resolved).contains(subject);
        verify(identityService).resolve(provider, principal);
    }

}
