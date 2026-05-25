package io.docpilot.block.processing;

import io.docpilot.block.model.BlockDocument;
import io.docpilot.block.model.BlockNode;
import io.docpilot.block.model.BlockType;
import io.docpilot.block.model.HtmlDisplayMode;
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
                .anySatisfy(inline -> assertThat(inline.getMarks()).contains(MarkType.BOLD))
                .anySatisfy(inline -> assertThat(inline.getMarks()).contains(MarkType.ITALIC))
                .anySatisfy(inline -> assertThat(inline.getMarks()).contains(MarkType.STRIKE))
                .anySatisfy(inline -> {
                    assertThat(inline.getType()).isEqualTo(InlineType.LINK);
                    assertThat(inline.getAttrs()).containsEntry("href", "https://example.com");
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
                .containsEntry("displayMode", HtmlDisplayMode.FIXED)
                .containsEntry("fixedHeightPx", 320)
                .containsEntry("allowScripts", false)
                .containsEntry("source", "<div><p>2026 Q2 sales report</p></div>");
        assertThat(html.getAttrs()).doesNotContainKeys("plainText", "syncId");
        assertThat(html.getAttrs().get("id")).isEqualTo(html.getId());
        assertThat(html.getId()).matches("[0-9a-f]{32}");
    }

}
