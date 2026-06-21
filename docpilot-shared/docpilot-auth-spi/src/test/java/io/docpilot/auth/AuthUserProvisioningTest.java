package io.docpilot.auth;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AuthUserProvisioningTest {

    @Test
    void defaultsToPrincipalProfileWithoutUserId() {
        AuthPrincipal principal = AuthPrincipal.of(
                "remote-user",
                "alice",
                "alice@example.com",
                "Alice",
                Set.of("user")
        );

        AuthUserProvisioning provisioning = AuthUserProvisioning.from(principal);

        assertThat(provisioning.userId()).isNull();
        assertThat(provisioning.email()).isEqualTo("alice@example.com");
        assertThat(provisioning.displayName()).isEqualTo("Alice");
    }

    @Test
    void providerDefaultProvisioningUsesPrincipalProfile() {
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
                return Optional.empty();
            }
        };
        AuthPrincipal principal = AuthPrincipal.of(
                "remote-user",
                "alice",
                "alice@example.com",
                "Alice",
                Set.of("user")
        );

        AuthUserProvisioning provisioning = provider.userProvisioning(principal);

        assertThat(provisioning.userId()).isNull();
        assertThat(provisioning.email()).isEqualTo("alice@example.com");
        assertThat(provisioning.displayName()).isEqualTo("Alice");
    }

}
