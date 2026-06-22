package io.docpilot.controller;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.docpilot.common.result.StatusCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "docpilot.workspace.mongo.init-indexes=false",
        "docpilot.knowledge.index.mode=direct",
        "docpilot.user-settings.cache.enabled=false",
        "docpilot.inline-completion.prompts.complete.system=SYSTEM {{shape}} {{candidateCount}} {{candidateTokenLimit}}",
        "docpilot.inline-completion.prompts.complete.user=Before={{textBeforeCursor}} Shape={{responseJsonShape}}"
})
@AutoConfigureMockMvc
@Import(WorkspaceControllerTestConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registerLoginAndMeReturnJwtBackedUser() throws Exception {
        String email = uniqueEmail();
        JsonNode registered = postJson("/auth/register", """
                {"email":" %s ","password":"password123","displayName":" Alice "}
                """.formatted(email.toUpperCase()), null);

        assertThat(registered.has("token")).isFalse();
        assertThat(registered.get("email").asText()).isEqualTo(email);
        assertThat(registered.get("displayName").asText()).isEqualTo("Alice");

        JsonNode loggedIn = postJson("/auth/login", """
                {"email":"%s","password":"password123"}
                """.formatted(email), null);
        String token = loggedIn.get("token").asText();
        assertThat(token).contains(".");
        assertThat(loggedIn.get("user").get("email").asText()).isEqualTo(email);

        mockMvc.perform(post("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.displayName").value("Alice"));

        mockMvc.perform(post("/workspace/list")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workspaces").isArray());
    }

    @Test
    void duplicateEmailReturnsConflictAndBadLoginReturnsUnauthorized() throws Exception {
        String email = uniqueEmail();
        postJson("/auth/register", """
                {"email":"%s","password":"password123","displayName":"Alice"}
                """.formatted(email), null);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","displayName":"Alice"}
                                """.formatted(email)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(StatusCode.CONFLICT.code()));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"wrong-password"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(StatusCode.UNAUTHORIZED.code()));
    }

    @Test
    void changePasswordAndDisplayNameRequireAuth() throws Exception {
        String email = uniqueEmail();
        postJson("/auth/register", """
                {"email":"%s","password":"password123","displayName":"Alice"}
                """.formatted(email), null);
        String token = postJson("/auth/login", """
                {"email":"%s","password":"password123"}
                """.formatted(email), null).get("token").asText();

        mockMvc.perform(post("/auth/display-name/change")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Alice Updated"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Alice Updated"));

        mockMvc.perform(post("/auth/password/change")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"password123","newPassword":"changed123"}
                                """))
                .andExpect(status().isOk());

        postJson("/auth/login", """
                {"email":"%s","password":"changed123"}
                """.formatted(email), null);
    }

    private JsonNode postJson(String path, String json, String token) throws Exception {
        var builder = post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        MvcResult result = mockMvc.perform(builder)
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID().toString().replace("-", "") + "@example.com";
    }

}
