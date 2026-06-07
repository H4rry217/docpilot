package io.docpilot.block.typed;

import io.docpilot.block.model.BlockDocument;
import io.docpilot.block.model.BlockNode;
import io.docpilot.block.model.BlockType;
import io.docpilot.block.model.HtmlDisplayMode;
import io.docpilot.block.model.InlineNode;
import io.docpilot.block.model.InlineType;
import io.docpilot.block.processing.MarkdownBlockParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BlockNodeConverterTest {

    @Test
    public void convertsLongMarkdownDocumentToTypedDocument() {
        MarkdownBlockParser parser = new MarkdownBlockParser();
        BlockDocument document = parser.parse("""
                ---
                title: Typed View Spec
                tags: [blocks, typed]
                ---

                # Typed Block View

                Intro paragraph with **bold** text and [a link](https://example.com).

                [TOC]

                > [!WARNING] Migration boundary
                > Keep canonical blocks stable while using typed nodes internally.

                3. First ordered item
                4. Second ordered item

                - [x] Preserve unknown attrs
                - [ ] Validate bad attrs later

                ```java
                System.out.println("typed");
                ```

                ```mermaid
                graph TD
                  A-->B
                ```

                $$
                a^2 + b^2 = c^2
                $$

                | Name | Value |
                | :--- | ---: |
                | typed | yes |

                <section data-docpilot="html">HTML source</section>

                Footnote here[^typed].

                [^typed]: Footnote body
                """);

        TypedBlockDocument typedDocument = BlockNodeConverter.toTyped(document);
        List<TypedBlockNode> allTypedNodes = flatten(typedDocument.blocks());

        assertFalse(document.getBlocks().isEmpty());
        assertEquals(document.getBlocks().size(), typedDocument.blocks().size());
        assertTrue(String.valueOf(first(allTypedNodes, FrontMatterBlock.class).data()).contains("Typed View Spec"));
        assertEquals(1, first(allTypedNodes, HeadingBlock.class).level());
        assertEquals("[TOC]", first(allTypedNodes, TocBlock.class).raw().trim());
        assertEquals("warning", first(allTypedNodes, CalloutBlock.class).kind());
        assertEquals(3, first(allTypedNodes, OrderedListBlock.class).start());
        assertEquals(true, first(allTypedNodes, TaskListItemBlock.class).checked());
        assertEquals("java", first(allTypedNodes, CodeBlock.class).language());
        assertTrue(allTypedNodes.stream()
                .filter(CodeBlock.class::isInstance)
                .map(CodeBlock.class::cast)
                .map(CodeBlock::language)
                .toList()
                .contains("mermaid"));
        assertFalse(first(allTypedNodes, MathBlock.class).text().isBlank());
        assertEquals(TableCellAlignment.LEFT, first(allTypedNodes, TableCellBlock.class).alignment());
        assertEquals(HtmlDisplayMode.FIXED, first(allTypedNodes, HtmlBlock.class).displayMode());
        assertEquals("typed", first(allTypedNodes, FootnoteDefinitionBlock.class).label());
    }

    @Test
    public void roundTripsTypedBlockWithoutLosingExtraAttrs() {
        BlockNode heading = BlockNode.of(
                "heading1",
                BlockType.HEADING,
                Map.of("level", "2", "tracking", "keep-me"),
                List.of(InlineNode.of(InlineType.TEXT, "Title", Map.of(), List.of(), null)),
                List.of(),
                null
        );

        TypedBlockNode typed = BlockNodeConverter.toTyped(heading);
        BlockNode roundTripped = BlockNodeConverter.toBlockNode(typed);

        HeadingBlock headingBlock = assertInstanceOf(HeadingBlock.class, typed);
        assertEquals(2, headingBlock.level());
        assertEquals(2, roundTripped.getAttrs().get("level"));
        assertEquals("keep-me", roundTripped.getAttrs().get("tracking"));
        assertEquals(1, roundTripped.getInlines().size());
    }

    @Test
    public void normalizesLegacyHtmlDisplayMode() {
        BlockNode html = BlockNode.of(
                "html1",
                BlockType.HTML_BLOCK,
                Map.of(
                        "source", "<div>hello</div>",
                        "displayMode", HtmlDisplayMode.FIT,
                        "fixedHeightPx", 2000,
                        "extra", "preserve"
                ),
                List.of(),
                List.of(),
                null
        );

        TypedBlockNode typed = BlockNodeConverter.toTyped(html);
        BlockNode roundTripped = BlockNodeConverter.toBlockNode(typed);

        HtmlBlock htmlBlock = assertInstanceOf(HtmlBlock.class, typed);
        assertEquals(HtmlDisplayMode.FIXED, htmlBlock.displayMode());
        assertEquals(320, htmlBlock.fixedHeightPx());
        assertEquals("fixed", roundTripped.getAttrs().get("displayMode"));
        assertEquals(320, roundTripped.getAttrs().get("fixedHeightPx"));
        assertEquals("preserve", roundTripped.getAttrs().get("extra"));
    }

    @Test
    public void validatesInvalidTypedAttrsWithoutBlockingConversion() {
        BlockNode heading = BlockNode.of(
                "bad-heading",
                BlockType.HEADING,
                Map.of("level", "large"),
                List.of(),
                List.of(),
                null
        );

        TypedBlockNode typed = BlockNodeConverter.toTyped(heading);
        List<ValidationIssue> issues = BlockNodeConverter.validate(BlockDocument.of(List.of(heading)));

        HeadingBlock headingBlock = assertInstanceOf(HeadingBlock.class, typed);
        assertEquals(1, headingBlock.level());
        assertFalse(issues.stream()
                .filter(issue -> "blocks[0]".equals(issue.path()))
                .filter(issue -> BlockType.HEADING == issue.blockType())
                .filter(issue -> "level".equals(issue.attrKey()))
                .filter(issue -> ValidationSeverity.WARNING == issue.severity())
                .toList()
                .isEmpty());
    }

    @Test
    public void everyBlockTypeHasAdapterOrFallback() {
        for (BlockType type : BlockType.values()) {
            BlockNode block = BlockNode.of(type.name().toLowerCase(), type, Map.of(), List.of(), List.of(), null);

            TypedBlockNode typed = BlockNodeConverter.toTyped(block);
            BlockNode roundTripped = BlockNodeConverter.toBlockNode(typed);

            assertNotNull(typed);
            assertEquals(type, roundTripped.getType());
        }
    }

    private static List<TypedBlockNode> flatten(List<TypedBlockNode> nodes) {
        List<TypedBlockNode> result = new ArrayList<>();
        if (nodes == null) {
            return result;
        }
        for (TypedBlockNode node : nodes) {
            collect(node, result);
        }
        return result;
    }

    private static void collect(TypedBlockNode node, List<TypedBlockNode> result) {
        if (node == null) {
            return;
        }
        result.add(node);
        for (TypedBlockNode child : childrenOf(node)) {
            collect(child, result);
        }
    }

    private static List<TypedBlockNode> childrenOf(TypedBlockNode node) {
        if (node instanceof CalloutBlock callout) {
            return callout.children();
        }
        if (node instanceof FootnoteDefinitionBlock footnoteDefinition) {
            return footnoteDefinition.children();
        }
        if (node instanceof GenericTypedBlock generic) {
            return generic.children();
        }
        if (node instanceof OrderedListBlock orderedList) {
            return orderedList.children();
        }
        if (node instanceof TaskListItemBlock taskListItem) {
            return taskListItem.children();
        }
        return List.of();
    }

    private static <T extends TypedBlockNode> T first(List<TypedBlockNode> nodes, Class<T> type) {
        for (TypedBlockNode node : nodes) {
            if (type.isInstance(node)) {
                return type.cast(node);
            }
        }
        throw new AssertionError("Missing typed block: " + type.getSimpleName());
    }

}
