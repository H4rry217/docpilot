package io.docpilot.block.processing;

import io.docpilot.block.model.BlockDocument;
import io.docpilot.block.model.BlockNode;
import io.docpilot.block.model.BlockType;
import io.docpilot.block.model.InlineMark;
import io.docpilot.block.model.InlineType;
import io.docpilot.block.model.MarkType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownBlockParserTest {

    private final MarkdownBlockParser parser = new MarkdownBlockParser();

    @Test
    void parseBasicMarkdownAndInlineMarks() {
        BlockDocument document = parser.parse("""
                # Title

                Hello **bold** and *italic* and ~~gone~~ with [link](https://example.com) ![alt](img.png)
                """);

        assertThat(document.getSchemaVersion()).isEqualTo(BlockDocument.CURRENT_SCHEMA_VERSION);
        assertThat(document.getBlocks()).hasSize(2);
        assertThat(document.getBlocks()).allSatisfy(block -> assertThat(block.getId()).matches("[0-9a-f]{32}"));
        assertThat(document.getBlocks().getFirst().getType()).isEqualTo(BlockType.HEADING);
        assertThat(document.getBlocks().getFirst().getAttrs()).containsEntry("level", 1);

        BlockNode paragraph = document.getBlocks().get(1);
        assertThat(paragraph.getType()).isEqualTo(BlockType.PARAGRAPH);
        assertThat(paragraph.getInlines())
                .anySatisfy(inline -> assertThat(hasMark(inline.getMarks(), MarkType.BOLD)).isTrue())
                .anySatisfy(inline -> assertThat(hasMark(inline.getMarks(), MarkType.ITALIC)).isTrue())
                .anySatisfy(inline -> assertThat(hasMark(inline.getMarks(), MarkType.STRIKE)).isTrue())
                .anySatisfy(inline -> {
                    assertThat(inline.getText()).isEqualTo("link");
                    assertThat(inline.getMarks())
                            .anySatisfy(mark -> {
                                assertThat(mark.getType()).isEqualTo(MarkType.LINK);
                                assertThat(mark.getAttrs()).containsEntry("href", "https://example.com");
                            });
                })
                .anySatisfy(inline -> {
                    assertThat(inline.getType()).isEqualTo(InlineType.IMAGE);
                    assertThat(inline.getAttrs()).containsEntry("src", "img.png");
                });
    }

    @Test
    void parseGfmTablesAndTaskListItems() {
        BlockDocument document = parser.parse("""
                - [x] Done
                - [ ] Todo

                | Name | Value |
                | --- | --- |
                | A | 1 |
                """);

        assertThat(document.getBlocks()).hasSize(2);
        assertThat(document.getBlocks().getFirst().getType()).isEqualTo(BlockType.BULLET_LIST);
        assertThat(document.getBlocks().getFirst().getChildren())
                .extracting(BlockNode::getType)
                .containsExactly(BlockType.TASK_LIST_ITEM, BlockType.TASK_LIST_ITEM);
        assertThat(document.getBlocks().getFirst().getChildren().getFirst().getAttrs()).containsEntry("checked", true);

        BlockNode table = document.getBlocks().get(1);
        assertThat(table.getType()).isEqualTo(BlockType.TABLE);
        assertThat(table.getChildren()).extracting(BlockNode::getType).containsExactly(BlockType.TABLE_ROW, BlockType.TABLE_ROW);
        assertThat(table.getChildren().getFirst().getChildren()).extracting(BlockNode::getType).containsExactly(BlockType.TABLE_CELL, BlockType.TABLE_CELL);
    }

    @Test
    void preserveHtmlBlockWithDocPilotAttrs() {
        BlockDocument document = parser.parse("<div><p>2026 Q2 sales report</p></div>");

        assertThat(document.getBlocks()).hasSize(1);
        BlockNode html = document.getBlocks().getFirst();
        assertThat(html.getType()).isEqualTo(BlockType.HTML_BLOCK);
        assertThat(html.getAttrs())
                .containsEntry("title", "HTML")
                .containsEntry("displayMode", "fixed")
                .containsEntry("fixedHeightPx", 320)
                .containsEntry("allowScripts", false)
                .containsEntry("source", "<div><p>2026 Q2 sales report</p></div>");
        assertThat(html.getAttrs()).doesNotContainKeys("plainText", "syncId");
        assertThat(html.getAttrs().get("id")).isEqualTo(html.getId());
        assertThat(html.getId()).matches("[0-9a-f]{32}");
    }

    @Test
    void parseEnhancedBlocksAndInlineMarks() {
        BlockDocument document = parser.parse("""
                ---
                title: Spec
                tags: [ai, docs]
                ---

                > [!NOTE] Read me
                > Body with $x^2$, ==hot==, ++new++, ^up^, ~down~, <u>under</u>, and [**deep**](https://example.com).

                $$
                a^2 + b^2 = c^2
                $$

                ```mermaid
                graph TD
                  A-->B
                ```

                Term
                : Definition body

                Footnote here[^one].

                [^one]: Footnote body

                [TOC]
                """);

        assertThat(document.getSchemaVersion()).isEqualTo("docpilot-block/2");
        assertThat(document.getBlocks()).extracting(BlockNode::getType)
                .contains(BlockType.FRONT_MATTER, BlockType.CALLOUT, BlockType.MATH_BLOCK,
                        BlockType.DIAGRAM_BLOCK, BlockType.DEFINITION_LIST, BlockType.FOOTNOTE_DEFINITION, BlockType.TOC);

        BlockNode frontMatter = document.getBlocks().getFirst();
        assertThat(frontMatter.getAttrs()).containsEntry("format", "yaml");
        assertThat(frontMatter.getAttrs().get("data")).asString().contains("Spec");

        BlockNode callout = document.getBlocks().stream()
                .filter(block -> block.getType() == BlockType.CALLOUT)
                .findFirst()
                .orElseThrow();
        assertThat(callout.getAttrs())
                .containsEntry("kind", "note")
                .containsEntry("title", "Read me");
        BlockNode calloutParagraph = callout.getChildren().getFirst();
        assertThat(calloutParagraph.getInlines())
                .anySatisfy(inline -> assertThat(inline.getType()).isEqualTo(InlineType.MATH_INLINE))
                .anySatisfy(inline -> assertThat(hasMark(inline.getMarks(), MarkType.HIGHLIGHT)).isTrue())
                .anySatisfy(inline -> assertThat(hasMark(inline.getMarks(), MarkType.INSERT)).isTrue())
                .anySatisfy(inline -> assertThat(hasMark(inline.getMarks(), MarkType.SUPERSCRIPT)).isTrue())
                .anySatisfy(inline -> assertThat(hasMark(inline.getMarks(), MarkType.SUBSCRIPT)).isTrue())
                .anySatisfy(inline -> assertThat(hasMark(inline.getMarks(), MarkType.UNDERLINE)).isTrue())
                .anySatisfy(inline -> {
                    assertThat(inline.getText()).isEqualTo("deep");
                    assertThat(hasMark(inline.getMarks(), MarkType.BOLD)).isTrue();
                    assertThat(hasMark(inline.getMarks(), MarkType.LINK)).isTrue();
                });

        BlockNode math = document.getBlocks().stream()
                .filter(block -> block.getType() == BlockType.MATH_BLOCK)
                .findFirst()
                .orElseThrow();
        assertThat(math.getAttrs()).containsEntry("notation", "latex");
        assertThat(math.getAttrs().get("text")).asString().contains("a^2 + b^2");

        BlockNode diagram = document.getBlocks().stream()
                .filter(block -> block.getType() == BlockType.DIAGRAM_BLOCK)
                .findFirst()
                .orElseThrow();
        assertThat(diagram.getAttrs()).containsEntry("engine", "mermaid");

        BlockNode footnoteDefinition = document.getBlocks().stream()
                .filter(block -> block.getType() == BlockType.FOOTNOTE_DEFINITION)
                .findFirst()
                .orElseThrow();
        assertThat(footnoteDefinition.getAttrs()).containsEntry("label", "one");
    }

    private boolean hasMark(Iterable<InlineMark> marks, MarkType type) {
        for (InlineMark mark : marks) {
            if (mark.getType() == type) {
                return true;
            }
        }
        return false;
    }

}
