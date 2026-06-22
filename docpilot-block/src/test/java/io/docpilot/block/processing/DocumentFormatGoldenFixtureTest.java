package io.docpilot.block.processing;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.docpilot.block.model.BlockDocument;
import io.docpilot.block.model.BlockNode;
import io.docpilot.block.model.BlockType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentFormatGoldenFixtureTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MarkdownBlockParser parser = new MarkdownBlockParser();
    private final MarkdownBlockRenderer renderer = new MarkdownBlockRenderer();
    private final BlockDocumentNormalizer normalizer = new BlockDocumentNormalizer(parser);
    private final ProseMirrorJsonConverter proseMirrorJsonConverter = new ProseMirrorJsonConverter();

    @Test
    void convertCanonicalBlockJsonToSharedProseMirrorContract() throws IOException {
        BlockDocument blockDocument = readBlockFixture("mixed.block.json");
        JsonNode expected = readJsonFixture("mixed.prosemirror.json");
        JsonNode actual = MAPPER.valueToTree(proseMirrorJsonConverter.toProseMirror(blockDocument));

        assertThat(normalizeJson(actual)).isEqualTo(normalizeJson(expected));
    }

    @Test
    void parseNormalizeAndRenderMarkdownFixtureWithoutDroppingHighRiskBlocks() throws IOException {
        String markdown = readTextFixture("mixed.md");

        BlockDocument parsed = parser.parse(markdown);
        BlockDocument normalized = normalizer.normalizeForEditing(parsed);
        String rendered = renderer.render(normalized);
        BlockDocument reparsed = parser.parse(rendered);

        assertThat(blockTypes(normalized))
                .contains(BlockType.HEADING, BlockType.PARAGRAPH, BlockType.CALLOUT, BlockType.TABLE,
                        BlockType.CODE_BLOCK, BlockType.MATH_BLOCK, BlockType.HTML_BLOCK, BlockType.FOOTNOTE_DEFINITION);
        assertThat(findBlock(normalized, BlockType.CALLOUT).getAttrs()).containsEntry("kind", "tip");
        assertThat(normalized.getBlocks().stream()
                .filter(block -> block.getType() == BlockType.CALLOUT)
                .anyMatch(block -> Boolean.TRUE.equals(block.getAttrs().get("collapsible"))
                        && "details".equals(block.getAttrs().get("kind")))).isTrue();
        assertThat(blockTypes(reparsed))
                .contains(BlockType.TABLE, BlockType.CODE_BLOCK, BlockType.MATH_BLOCK, BlockType.HTML_BLOCK,
                        BlockType.FOOTNOTE_DEFINITION);
    }

    private BlockDocument readBlockFixture(String name) throws IOException {
        return MAPPER.readValue(fixtureRoot().resolve(name).toFile(), BlockDocument.class);
    }

    private JsonNode readJsonFixture(String name) throws IOException {
        return MAPPER.readTree(fixtureRoot().resolve(name).toFile());
    }

    private String readTextFixture(String name) throws IOException {
        return Files.readString(fixtureRoot().resolve(name));
    }

    private Path fixtureRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("test-fixtures").resolve("document-format");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate test-fixtures/document-format");
    }

    private JsonNode normalizeJson(JsonNode node) {
        JsonNode copy = node.deepCopy();
        normalizeJsonInPlace(copy);
        return copy;
    }

    private void normalizeJsonInPlace(JsonNode node) {
        if (node.isArray()) {
            node.forEach(this::normalizeJsonInPlace);
            return;
        }

        if (!node.isObject()) {
            return;
        }

        List<String> emptyFields = new ArrayList<>();
        node.properties().forEach(field -> {
            normalizeJsonInPlace(field.getValue());
            if (field.getValue().isNull() || isEmptyContainerField(field.getKey(), field.getValue())) {
                emptyFields.add(field.getKey());
            }
        });
        emptyFields.forEach(field -> ((tools.jackson.databind.node.ObjectNode) node).remove(field));
    }

    private boolean isEmptyContainerField(String key, JsonNode value) {
        return List.of("attrs", "content", "marks").contains(key) && value.isContainer() && value.isEmpty();
    }

    private List<BlockType> blockTypes(BlockDocument document) {
        return allBlocks(document.getBlocks()).stream().map(BlockNode::getType).toList();
    }

    private BlockNode findBlock(BlockDocument document, BlockType type) {
        return allBlocks(document.getBlocks()).stream()
                .filter(block -> block.getType() == type)
                .findFirst()
                .orElseThrow();
    }

    private List<BlockNode> allBlocks(List<BlockNode> blocks) {
        List<BlockNode> all = new ArrayList<>();
        for (BlockNode block : blocks) {
            all.add(block);
            all.addAll(allBlocks(block.getChildren()));
        }
        return all;
    }

}
