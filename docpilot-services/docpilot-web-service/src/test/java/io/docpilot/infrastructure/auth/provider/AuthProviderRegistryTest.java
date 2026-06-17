package io.docpilot.infrastructure.auth.provider;

import io.docpilot.auth.AuthLoginFlow;
import io.docpilot.auth.AuthPrincipal;
import io.docpilot.auth.AuthProvider;
import io.docpilot.auth.AuthProviderCapabilities;
import io.docpilot.auth.AuthRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthProviderRegistryTest {

    @Test
    void resolvesConfiguredProvider() {
        AuthProviderProperties properties = new AuthProviderProperties();
        properties.setProvider("miniapp");
        AuthProvider provider = provider("miniapp");

        AuthProviderRegistry registry = new AuthProviderRegistry(properties, List.of(provider));

        assertThat(registry.currentProvider()).isSameAs(provider);
        assertThat(registry.currentProviderId()).isEqualTo("miniapp");
    }

    @Test
    void failsWhenConfiguredProviderIsMissing() {
        AuthProviderProperties properties = new AuthProviderProperties();
        properties.setProvider("missing");

        assertThatThrownBy(() -> new AuthProviderRegistry(properties, List.of(provider("miniapp"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Configured auth provider was not found: missing");
    }

    @Test
    void failsWhenProviderIdsAreDuplicated() {
        AuthProviderProperties properties = new AuthProviderProperties();
        properties.setProvider("miniapp");

        assertThatThrownBy(() -> new AuthProviderRegistry(properties, List.of(provider("miniapp"), provider("miniapp"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate auth provider id: miniapp");
    }

    private AuthProvider provider(String providerId) {
        return new AuthProvider() {
            @Override
            public String providerId() {
                return providerId;
            }

            @Override
            public AuthProviderCapabilities capabilities() {
                return new AuthProviderCapabilities(Set.of(AuthLoginFlow.HOST_TOKEN), Set.of());
            }

            @Override
            public Optional<AuthPrincipal> authenticate(AuthRequest request) {
                return Optional.empty();
            }
        };
    }

}
