package io.docpilot.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.docpilot.common.result.StatusCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "docpilot.auth.jwt.secret=docpilot-dev-secret",
        "docpilot.workspace.mongo.init-indexes=false"
})
@AutoConfigureMockMvc
@Import(WorkspaceControllerTestConfig.class)
class DocumentWorkspaceControllerTest {

    private static final String AUTHORIZATION = "Bearer " + testJwt();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void ensureDefaultWorkspaceIsIdempotentAndUsesStringIds() throws Exception {
        JsonNode first = postJson("/workspace/default/ensure", "{}");
        JsonNode second = postJson("/workspace/default/ensure", "{}");

        assertThat(first.get("workspaceId").isTextual()).isTrue();
        assertThat(first.get("rootNodeId").isTextual()).isTrue();
        assertThat(first.get("type").isNumber()).isTrue();
        assertThat(first.get("type").asInt()).isEqualTo(1);
        assertThat(second.get("workspaceId").asText()).isEqualTo(first.get("workspaceId").asText());
        assertThat(second.get("rootNodeId").asText()).isEqualTo(first.get("rootNodeId").asText());
    }

    @Test
    void defaultWorkspaceCannotBeDeleted() throws Exception {
        JsonNode defaultWorkspace = postJson("/workspace/default/ensure", "{}");
        String workspaceId = defaultWorkspace.get("workspaceId").asText();

        mockMvc.perform(post("/workspace/delete")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workspaceId":"%s"}
                                """.formatted(workspaceId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(StatusCode.BAD_REQUEST.code()));

        JsonNode workspaces = postJson("/workspace/list", "{}").get("workspaces");
        boolean defaultWorkspaceStillExists = false;
        for (JsonNode workspace : workspaces) {
            if (workspaceId.equals(workspace.get("workspaceId").asText())) {
                defaultWorkspaceStillExists = true;
                break;
            }
        }
        assertThat(defaultWorkspaceStillExists).isTrue();
    }

    @Test
    void workspaceCanBeCreatedRenamedAndDeleted() throws Exception {
        String workspaceName = uniqueName("Workspace");
        JsonNode created = postJson("/workspace/create", """
                {"name":"%s"}
                """.formatted(workspaceName));
        String workspaceId = created.get("workspaceId").asText();
        assertThat(created.get("name").asText()).isEqualTo(workspaceName);
        assertThat(created.get("type").asInt()).isEqualTo(2);
        assertThat(created.get("rootNodeId").isTextual()).isTrue();

        JsonNode another = postJson("/workspace/create", """
                {"name":"%s"}
                """.formatted(uniqueName("Workspace")));
        assertThat(another.get("type").asInt()).isEqualTo(2);

        String renamedName = uniqueName("Renamed");
        JsonNode renamed = postJson("/workspace/rename", """
                {"workspaceId":"%s","name":"%s"}
                """.formatted(workspaceId, renamedName));
        assertThat(renamed.get("name").asText()).isEqualTo(renamedName);

        JsonNode tree = postJson("/workspace/tree/get", """
                {"workspaceId":"%s"}
                """.formatted(workspaceId));
        assertThat(tree.get("workspace").get("name").asText()).isEqualTo(renamedName);
        assertThat(tree.get("nodes").get(0).get("name").asText()).isEqualTo(renamedName);

        postJson("/workspace/delete", """
                {"workspaceId":"%s"}
                """.formatted(workspaceId));
        JsonNode workspaces = postJson("/workspace/list", "{}").get("workspaces");
        for (JsonNode workspace : workspaces) {
            assertThat(workspace.get("workspaceId").asText()).isNotEqualTo(workspaceId);
        }

        mockMvc.perform(post("/workspace/tree/get")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workspaceId":"%s"}
                                """.formatted(workspaceId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(StatusCode.NOT_FOUND.code()));
    }

    @Test
    void createDocumentCreatesNodeAndCanBeRead() throws Exception {
        SeededDocument seededDocument = createDocumentInWorkspace();

        mockMvc.perform(post("/document/get")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentId":"%s"}
                                """.formatted(seededDocument.documentId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.document.documentId").value(seededDocument.documentId()))
                .andExpect(jsonPath("$.data.document.currentVersion").value("1"))
                .andExpect(jsonPath("$.data.document.content.blockSchemaVersion").value("docpilot-block/2"))
                .andExpect(jsonPath("$.data.prosemirror.type").value("doc"));
    }

    @Test
    void saveDocumentContentChecksBaseVersion() throws Exception {
        SeededDocument seededDocument = createDocumentInWorkspace();
        JsonNode document = postJson("/document/get", """
                {"documentId":"%s"}
                """.formatted(seededDocument.documentId())).get("document");
        String version = document.get("currentVersion").asText();

        mockMvc.perform(post("/document/content/save")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "documentId":"%s",
                                  "baseVersion":"%s",
                                  "clientMutationId":"mutation-1",
                                  "blockDocument":%s
                                }
                                """.formatted(seededDocument.documentId(), version, blockDocumentJson("Saved body"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.document.currentVersion").value("2"))
                .andExpect(jsonPath("$.data.prosemirror.type").value("doc"));

        mockMvc.perform(post("/document/content/save")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "documentId":"%s",
                                  "baseVersion":"%s",
                                  "clientMutationId":"mutation-2",
                                  "blockDocument":%s
                                }
                                """.formatted(seededDocument.documentId(), version, blockDocumentJson("Conflict body"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(StatusCode.CONFLICT.code()));
    }

    @Test
    void saveDocumentContentIsIdempotentByClientMutationId() throws Exception {
        SeededDocument seededDocument = createDocumentInWorkspace();
        JsonNode document = postJson("/document/get", """
                {"documentId":"%s"}
                """.formatted(seededDocument.documentId())).get("document");
        String version = document.get("currentVersion").asText();
        String savedBlockDocument = blockDocumentJson("Saved body");

        JsonNode firstSave = postJson("/document/content/save", """
                {
                  "documentId":"%s",
                  "baseVersion":"%s",
                  "clientMutationId":"mutation-idempotent",
                  "blockDocument":%s
                }
                """.formatted(seededDocument.documentId(), version, savedBlockDocument)).get("document");
        assertThat(firstSave.get("currentVersion").asText()).isEqualTo("2");

        JsonNode duplicateSave = postJson("/document/content/save", """
                {
                  "documentId":"%s",
                  "baseVersion":"%s",
                  "clientMutationId":"mutation-idempotent",
                  "blockDocument":%s
                }
                """.formatted(seededDocument.documentId(), version, savedBlockDocument)).get("document");
        assertThat(duplicateSave.get("currentVersion").asText()).isEqualTo("2");

        JsonNode revisions = postJson("/document/revision/list", """
                {"documentId":"%s","limit":10}
                """.formatted(seededDocument.documentId())).get("revisions");
        assertThat(revisions).hasSize(2);
        assertThat(revisions.get(0).get("version").asText()).isEqualTo("2");
        assertThat(revisions.get(1).get("version").asText()).isEqualTo("1");

        mockMvc.perform(post("/document/content/save")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "documentId":"%s",
                                  "baseVersion":"%s",
                                  "clientMutationId":"mutation-idempotent",
                                  "blockDocument":%s
                                }
                                """.formatted(seededDocument.documentId(), version, blockDocumentJson("Different body"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(StatusCode.CONFLICT.code()));

        mockMvc.perform(post("/document/content/save")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "documentId":"%s",
                                  "baseVersion":"%s",
                                  "clientMutationId":"mutation-stale",
                                  "blockDocument":%s
                                }
                                """.formatted(seededDocument.documentId(), version, blockDocumentJson("Stale body"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(StatusCode.CONFLICT.code()));
    }

    @Test
    void duplicateActiveNodeNameReturnsConflictButDeletedNameCanBeReused() throws Exception {
        JsonNode workspace = postJson("/workspace/default/ensure", "{}");
        String workspaceId = workspace.get("workspaceId").asText();
        String rootNodeId = workspace.get("rootNodeId").asText();
        String folderName = uniqueName("Folder");

        JsonNode first = postJson("/workspace/node/create", """
                {"workspaceId":"%s","parentNodeId":"%s","name":"%s"}
                """.formatted(workspaceId, rootNodeId, folderName));

        mockMvc.perform(post("/workspace/node/create")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workspaceId":"%s","parentNodeId":"%s","name":"%s"}
                                """.formatted(workspaceId, rootNodeId, folderName)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(StatusCode.CONFLICT.code()));

        postJson("/workspace/node/delete", """
                {"nodeId":"%s"}
                """.formatted(first.get("nodeId").asText()));

        postJson("/workspace/node/create", """
                {"workspaceId":"%s","parentNodeId":"%s","name":"%s"}
                """.formatted(workspaceId, rootNodeId, folderName));
    }

    @Test
    void invalidIdReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/workspace/tree/get")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workspaceId":"not-a-number"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(StatusCode.BAD_REQUEST.code()));
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
        JsonNode workspace = postJson("/workspace/default/ensure", "{}");
        String workspaceId = workspace.get("workspaceId").asText();
        String rootNodeId = workspace.get("rootNodeId").asText();
        String folderName = uniqueName("Folder");
        String title = uniqueName("Spec");
        String nodeName = title + ".md";

        JsonNode folder = postJson("/workspace/node/create", """
                {"workspaceId":"%s","parentNodeId":"%s","name":"%s"}
                """.formatted(workspaceId, rootNodeId, folderName));
        String folderNodeId = folder.get("nodeId").asText();

        JsonNode document = postJson("/document/create", """
                {"workspaceId":"%s","parentNodeId":"%s","title":"%s","nodeName":"%s","markdown":"# Hello"}
                """.formatted(workspaceId, folderNodeId, title, nodeName)).get("document");
        String documentId = document.get("documentId").asText();

        JsonNode nodes = postJson("/workspace/tree/get", """
                {"workspaceId":"%s"}
                """.formatted(workspaceId)).get("nodes");
        boolean foundDocumentNode = false;
        for (JsonNode node : nodes) {
            if (node.hasNonNull("documentId") && documentId.equals(node.get("documentId").asText())) {
                assertThat(node.get("nodeType").isNumber()).isTrue();
                assertThat(node.get("nodeType").asInt()).isEqualTo(2);
                assertThat(node.get("resourceType").isNumber()).isTrue();
                assertThat(node.get("resourceType").asInt()).isEqualTo(1);
                foundDocumentNode = true;
                break;
            }
        }
        assertThat(foundDocumentNode).isTrue();
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

    private static String blockDocumentJson(String text) {
        return """
                {
                  "schemaVersion":"docpilot-block/2",
                  "blocks":[
                    {
                      "id":"b1",
                      "type":"PARAGRAPH",
                      "attrs":{},
                      "inlines":[{"type":"TEXT","text":"%s","attrs":{},"marks":[]}],
                      "children":[]
                    }
                  ],
                  "metadata":{}
                }
                """.formatted(text);
    }

    private static String testJwt() {
        String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = base64Url("{\"sub\":1,\"name\":\"HarryZ\",\"roles\":[\"admin\"],\"exp\":9999999999}");
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

    private static String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "");
    }

    private record SeededDocument(String documentId) {
    }
}
