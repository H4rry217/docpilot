package io.docpilot.auth;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRequestTest {

    @Test
    void readsHeadersCaseInsensitively() {
        AuthRequest request = new AuthRequest(
                Map.of("authorization", List.of("Bearer token-123")),
                "127.0.0.1",
                "http",
                "POST",
                "/auth/me"
        );

        assertThat(request.firstHeader("Authorization")).contains("Bearer token-123");
        assertThat(request.firstHeader("AUTHORIZATION")).contains("Bearer token-123");
        assertThat(request.bearerToken()).contains("token-123");
    }

    @Test
    void ignoresMissingOrBlankBearerTokens() {
        AuthRequest noAuthorization = new AuthRequest(Map.of(), null, null, null, null);
        AuthRequest blankBearer = new AuthRequest(
                Map.of("Authorization", List.of("Bearer   ")),
                null,
                null,
                null,
                null
        );

        assertThat(noAuthorization.bearerToken()).isEmpty();
        assertThat(blankBearer.bearerToken()).isEmpty();
    }

}
