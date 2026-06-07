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
    void parseInlineCodeMark() {
        BlockDocument document = parser.parse("使用 `System.out.println(\"Hello\")` 输出内容。");

        assertThat(document.getBlocks()).hasSize(1);
        BlockNode paragraph = document.getBlocks().getFirst();
        assertThat(paragraph.getType()).isEqualTo(BlockType.PARAGRAPH);
        assertThat(paragraph.getInlines())
                .noneSatisfy(inline -> assertThat(inline.getText()).contains("`"))
                .anySatisfy(inline -> {
                    assertThat(inline.getText()).isEqualTo("System.out.println(\"Hello\")");
                    assertThat(hasMark(inline.getMarks(), MarkType.CODE)).isTrue();
                });
    }

    @Test
    void parseEscapedMarkdownSyntaxAsLiteralText() {
        BlockDocument document = parser.parse("""
                \\#不是标题

                \\*不是斜体\\*

                转义 \\==不是高亮\\==、\\++不是插入\\++、\\^不是上标\\^、\\~不是下标\\~、\\$不是公式\\$、\\[^不是脚注]。
                """);

        assertThat(document.getBlocks()).hasSize(3);
        assertThat(document.getBlocks().getFirst().getType()).isEqualTo(BlockType.PARAGRAPH);
        assertThat(plainText(document.getBlocks().getFirst())).isEqualTo("#不是标题");

        BlockNode emphasisLike = document.getBlocks().get(1);
        assertThat(emphasisLike.getType()).isEqualTo(BlockType.PARAGRAPH);
        assertThat(plainText(emphasisLike)).isEqualTo("*不是斜体*");
        assertThat(emphasisLike.getInlines()).noneSatisfy(inline -> assertThat(inline.getMarks()).isNotEmpty());

        BlockNode customSyntax = document.getBlocks().get(2);
        assertThat(plainText(customSyntax))
                .isEqualTo("转义 ==不是高亮==、++不是插入++、^不是上标^、~不是下标~、$不是公式$、[^不是脚注]。");
        assertThat(customSyntax.getInlines()).noneSatisfy(inline -> assertThat(inline.getMarks()).isNotEmpty());
        assertThat(customSyntax.getInlines()).noneSatisfy(inline -> assertThat(inline.getType())
                .isIn(InlineType.MATH_INLINE, InlineType.FOOTNOTE_REF));
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
    void parseGithubDetailsAsCollapsibleCallout() {
        BlockDocument document = parser.parse("""
                <details>
                <summary>点击展开</summary>

                隐藏 **内容**
                </details>
                """);

        assertThat(document.getBlocks()).hasSize(1);
        BlockNode details = document.getBlocks().getFirst();
        assertThat(details.getType()).isEqualTo(BlockType.CALLOUT);
        assertThat(details.getAttrs())
                .containsEntry("kind", "details")
                .containsEntry("title", "点击展开")
                .containsEntry("collapsible", true)
                .containsEntry("open", false);
        assertThat(details.getChildren()).hasSize(1);
        assertThat(details.getChildren().getFirst().getType()).isEqualTo(BlockType.PARAGRAPH);
        assertThat(details.getChildren().getFirst().getInlines())
                .anySatisfy(inline -> {
                    assertThat(inline.getText()).isEqualTo("内容");
                    assertThat(hasMark(inline.getMarks(), MarkType.BOLD)).isTrue();
                });
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
                        BlockType.CODE_BLOCK, BlockType.DEFINITION_LIST, BlockType.FOOTNOTE_DEFINITION, BlockType.TOC);

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

        BlockNode mermaid = document.getBlocks().stream()
                .filter(block -> block.getType() == BlockType.CODE_BLOCK)
                .filter(block -> "mermaid".equals(block.getAttrs().get("language")))
                .findFirst()
                .orElseThrow();
        assertThat(mermaid.getAttrs()).containsEntry("text", "graph TD\n  A-->B\n");

        BlockNode footnoteDefinition = document.getBlocks().stream()
                .filter(block -> block.getType() == BlockType.FOOTNOTE_DEFINITION)
                .findFirst()
                .orElseThrow();
        assertThat(footnoteDefinition.getAttrs()).containsEntry("label", "one");
    }

    @Test
    void parseLatexMathWithoutAddingBackslashes() {
        BlockDocument document = parser.parse("""
                Inline $E=mc^2$.

                $$
                \\int_a^b f(x)dx
                $$
                """);

        BlockNode paragraph = document.getBlocks().getFirst();
        assertThat(paragraph.getType()).isEqualTo(BlockType.PARAGRAPH);
        assertThat(paragraph.getInlines())
                .anySatisfy(inline -> {
                    assertThat(inline.getType()).isEqualTo(InlineType.MATH_INLINE);
                    assertThat(inline.getText()).isEqualTo("E=mc^2");
                });

        BlockNode math = document.getBlocks().get(1);
        assertThat(math.getType()).isEqualTo(BlockType.MATH_BLOCK);
        assertThat(math.getAttrs()).containsEntry("text", "\\int_a^b f(x)dx");
    }

    private boolean hasMark(Iterable<InlineMark> marks, MarkType type) {
        for (InlineMark mark : marks) {
            if (mark.getType() == type) {
                return true;
            }
        }
        return false;
    }

    private String plainText(BlockNode block) {
        StringBuilder text = new StringBuilder();
        block.getInlines().forEach(inline -> text.append(inline.getText()));
        return text.toString();
    }

}
