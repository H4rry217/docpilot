package io.docpilot.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.docpilot.auth.AuthLoginFlow;
import io.docpilot.auth.AuthPrincipal;
import io.docpilot.auth.AuthProvider;
import io.docpilot.auth.AuthProviderCapabilities;
import io.docpilot.auth.AuthRequest;
import io.docpilot.common.exception.UnauthorizedException;
import io.docpilot.common.result.StatusCode;
import io.docpilot.infrastructure.auth.DefaultUserAccount;
import io.docpilot.infrastructure.auth.DefaultUserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "docpilot.auth.provider=miniapp",
        "docpilot.workspace.mongo.init-indexes=false",
        "docpilot.knowledge.index.mode=direct",
        "docpilot.user-settings.cache.enabled=false",
        "docpilot.inline-completion.prompts.complete.system=SYSTEM {{shape}} {{candidateCount}} {{candidateTokenLimit}}",
        "docpilot.inline-completion.prompts.complete.user=Before={{textBeforeCursor}} Shape={{responseJsonShape}}"
})
@AutoConfigureMockMvc
@Import({WorkspaceControllerTestConfig.class, CustomAuthProviderControllerTest.FakeAuthProviderConfig.class})
class CustomAuthProviderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DefaultUserAccountRepository accountRepository;

    @Test
    void configExposesCurrentProviderCapabilities() throws Exception {
        mockMvc.perform(post("/auth/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.providerId").value("miniapp"))
                .andExpect(jsonPath("$.data.capabilities.loginFlows[0]").value("HOST_TOKEN"))
                .andExpect(jsonPath("$.data.capabilities.supportsHostToken").value(true))
                .andExpect(jsonPath("$.data.capabilities.supportsPasswordLogin").value(false));
    }

    @Test
    void customProviderCreatesAndReusesUserIdentity() throws Exception {
        JsonNode first = postJson("/auth/me", "host-alice");
        JsonNode second = postJson("/auth/me", "host-alice");

        assertThat(first.get("userId").asLong()).isEqualTo(second.get("userId").asLong());
        assertThat(first.get("email").asText()).isEqualTo("alice@example.com");
        assertThat(first.get("displayName").asText()).isEqualTo("Alice");

        mockMvc.perform(post("/workspace/list")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer host-alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workspaces").isArray());
    }

    @Test
    void customProviderCreatesAndReusesPlaceholderIdentityWhenEmailIsMissing() throws Exception {
        JsonNode first = postJson("/auth/me", "host-opaque");
        JsonNode second = postJson("/auth/me", "host-opaque");

        assertThat(first.get("userId").asLong()).isEqualTo(second.get("userId").asLong());
        assertThat(first.get("email").asText()).endsWith("@provider.docpilot.invalid");
        assertThat(first.get("displayName").asText()).isEqualTo("Opaque User");
    }

    @Test
    void customProviderBindsExistingProviderManagedPlaceholderAccount() throws Exception {
        DefaultUserAccount account = new DefaultUserAccount();
        account.setEmail(placeholderEmail("miniapp", "openid-orphan"));
        account.setDisplayName("Existing Placeholder");
        account.setPasswordHash("provider:disabled");
        DefaultUserAccount savedAccount = accountRepository.create(account);

        JsonNode user = postJson("/auth/me", "host-orphan");

        assertThat(user.get("userId").asLong()).isEqualTo(savedAccount.getUserId());
        assertThat(user.get("email").asText()).isEqualTo(savedAccount.getEmail());
        assertThat(user.get("displayName").asText()).isEqualTo("Existing Placeholder");
    }

    @Test
    void passwordEndpointsAreUnavailableForCustomProvider() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","password":"password123"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(StatusCode.NOT_FOUND.code()));

        mockMvc.perform(post("/auth/password/change")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer host-alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"password123","newPassword":"changed123"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(StatusCode.NOT_FOUND.code()));
    }

    @Test
    void emailConflictDoesNotAutoBindExistingUser() throws Exception {
        postJson("/auth/me", "host-conflict-a");

        mockMvc.perform(post("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer host-conflict-b")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(StatusCode.CONFLICT.code()));
    }

    @Test
    void providerAuthFailureReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(StatusCode.UNAUTHORIZED.code()));
    }

    private JsonNode postJson(String path, String token) throws Exception {
        MvcResult result = mockMvc.perform(post(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    @TestConfiguration
    static class FakeAuthProviderConfig {

        @Bean
        AuthProvider miniappAuthProvider() {
            return new FakeMiniappAuthProvider();
        }

    }

    private static final class FakeMiniappAuthProvider implements AuthProvider {

        @Override
        public String providerId() {
            return "miniapp";
        }

        @Override
        public AuthProviderCapabilities capabilities() {
            return new AuthProviderCapabilities(Set.of(AuthLoginFlow.HOST_TOKEN), Set.of());
        }

        @Override
        public Optional<AuthPrincipal> authenticate(AuthRequest request) {
            return request.bearerToken()
                    .map(token -> switch (token) {
                        case "host-alice" -> AuthPrincipal.of(
                                providerId(), "openid-alice", "alice@example.com", "Alice", Set.of("user"));
                        case "host-conflict-a" -> AuthPrincipal.of(
                                providerId(), "openid-conflict-a", "conflict@example.com", "Conflict A", Set.of());
                        case "host-conflict-b" -> AuthPrincipal.of(
                                providerId(), "openid-conflict-b", "conflict@example.com", "Conflict B", Set.of());
                        case "host-opaque" -> AuthPrincipal.of(
                                providerId(), "openid-opaque", null, "Opaque User", Set.of());
                        case "host-orphan" -> AuthPrincipal.of(
                                providerId(), "openid-orphan", null, "Orphan User", Set.of());
                        default -> throw new UnauthorizedException("Invalid host token");
                    });
        }

    }

    private String placeholderEmail(String providerId, String subject) {
        return sha256(providerId + ":" + subject).substring(0, 32) + "@provider.docpilot.invalid";
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash test auth subject", e);
        }
    }

}
