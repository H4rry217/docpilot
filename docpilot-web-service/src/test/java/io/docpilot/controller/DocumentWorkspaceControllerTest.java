package io.docpilot.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.docpilot.common.result.StatusCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentWorkspaceControllerTest {

    private static final String AUTHORIZATION = "Bearer " + testJwt();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void listWorkspaceNodesAndReadDocument() throws Exception {
        SeededDocument seededDocument = createDocumentInWorkspace();

        mockMvc.perform(post("/document/get")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentId":"%s"}
                                """.formatted(seededDocument.documentId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.document.title").exists())
                .andExpect(jsonPath("$.data.document.blockDocument.schemaVersion").value("docpilot-block/1"))
                .andExpect(jsonPath("$.data.prosemirror.type").value("doc"))
                .andExpect(jsonPath("$.data.prosemirror.content[0].attrs.blockId").exists());
    }

    @Test
    void saveDocumentContentChecksExpectedVersion() throws Exception {
        SeededDocument seededDocument = createDocumentInWorkspace();
        JsonNode document = postJson("/document/get", """
                {"documentId":"%s"}
                """.formatted(seededDocument.documentId())).get("document");
        long version = document.get("version").asLong();

        mockMvc.perform(post("/document/content/save")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentId":"%s","markdown":"# Saved\\n\\nBody","expectedVersion":%d}
                                """.formatted(seededDocument.documentId(), version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.document.version").value((int) version + 1))
                .andExpect(jsonPath("$.data.prosemirror.type").value("doc"));

        mockMvc.perform(post("/document/content/save")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentId":"%s","markdown":"# Conflict","expectedVersion":%d}
                                """.formatted(seededDocument.documentId(), version)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(StatusCode.CONFLICT.code()));
    }

    @Test
    void workspaceListRequiresAuth() throws Exception {
        mockMvc.perform(post("/workspace/list")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(StatusCode.UNAUTHORIZED.code()));
    }

    private SeededDocument createDocumentInWorkspace() throws Exception {
        JsonNode workspace = postJson("/workspace/create", """
                {"name":"Test Workspace"}
                """);
        String workspaceId = workspace.get("workspaceId").asText();
        String rootNodeId = workspace.get("rootNodeId").asText();

        JsonNode folder = postJson("/workspace/node/create", """
                {"workspaceId":"%s","parentNodeId":"%s","type":"FOLDER","name":"Folder"}
                """.formatted(workspaceId, rootNodeId));
        String folderNodeId = folder.get("nodeId").asText();

        JsonNode document = postJson("/document/create", """
                {"title":"Spec","markdown":"# Hello"}
                """).get("document");
        String documentId = document.get("documentId").asText();

        postJson("/workspace/node/create", """
                {"workspaceId":"%s","parentNodeId":"%s","type":"DOCUMENT","name":"Spec.md","documentId":"%s"}
                """.formatted(workspaceId, folderNodeId, documentId));

        JsonNode children = postJson("/workspace/node/list", """
                {"workspaceId":"%s","parentNodeId":"%s"}
                """.formatted(workspaceId, folderNodeId)).get("nodes");
        assertThat(children).hasSize(1);
        assertThat(children.get(0).get("documentId").asText()).isEqualTo(documentId);
        return new SeededDocument(documentId);
    }

    private JsonNode postJson(String path, String json) throws Exception {
        MvcResult result = mockMvc.perform(post(path)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(node).isNotNull();
        return node.get("data");
    }

    private static String testJwt() {
        String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = base64Url("{\"sub\":\"dev-user\",\"name\":\"HarryZ\",\"roles\":[\"admin\"],\"exp\":9999999999}");
        String signingInput = header + "." + payload;
        return signingInput + "." + sign(signingInput);
    }

    private static String sign(String signingInput) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec("docpilot-dev-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String base64Url(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private record SeededDocument(String documentId) {
    }

}
