package io.docpilot.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentWorkspaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void listWorkspaceNodesAndReadSeededDocument() throws Exception {
        SeededDocument seededDocument = findSeededDocument();

        mockMvc.perform(post("/document/get")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentId":"%s"}
                                """.formatted(seededDocument.documentId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document.title").value("西游记.md"))
                .andExpect(jsonPath("$.document.blockDocument.schemaVersion").value("docpilot-block/1"))
                .andExpect(jsonPath("$.prosemirror.type").value("doc"))
                .andExpect(jsonPath("$.prosemirror.content[0].attrs.blockId").exists());
    }

    @Test
    void saveDocumentContentChecksExpectedVersion() throws Exception {
        SeededDocument seededDocument = findSeededDocument();
        JsonNode document = postJson("/document/get", """
                {"documentId":"%s"}
                """.formatted(seededDocument.documentId())).get("document");
        long version = document.get("version").asLong();

        mockMvc.perform(post("/document/content/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentId":"%s","markdown":"# 已保存\\n\\n正文","expectedVersion":%d}
                                """.formatted(seededDocument.documentId(), version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document.version").value((int) version + 1))
                .andExpect(jsonPath("$.prosemirror.type").value("doc"));

        mockMvc.perform(post("/document/content/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentId":"%s","markdown":"# 冲突","expectedVersion":%d}
                                """.formatted(seededDocument.documentId(), version)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_VERSION_CONFLICT"));
    }

    private SeededDocument findSeededDocument() throws Exception {
        JsonNode workspaceList = postJson("/workspace/list", "{}");
        JsonNode workspace = workspaceList.get("workspaces").get(0);
        String workspaceId = workspace.get("workspaceId").asText();
        String rootNodeId = workspace.get("rootNodeId").asText();

        JsonNode rootNodes = postJson("/workspace/node/list", """
                {"workspaceId":"%s","parentNodeId":"%s"}
                """.formatted(workspaceId, rootNodeId)).get("nodes");
        String folderNodeId = rootNodes.get(0).get("nodeId").asText();

        JsonNode children = postJson("/workspace/node/list", """
                {"workspaceId":"%s","parentNodeId":"%s"}
                """.formatted(workspaceId, folderNodeId)).get("nodes");

        for (JsonNode child : children) {
            if ("西游记.md".equals(child.get("name").asText())) {
                return new SeededDocument(child.get("documentId").asText());
            }
        }
        throw new AssertionError("Seeded document 西游记.md was not found");
    }

    private JsonNode postJson(String path, String json) throws Exception {
        MvcResult result = mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(node).isNotNull();
        return node;
    }

    private record SeededDocument(String documentId) {
    }

}
