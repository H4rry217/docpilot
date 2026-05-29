package io.docpilot.block.typed;

import io.docpilot.block.model.BlockType;
import io.docpilot.block.model.InlineNode;
import io.docpilot.block.model.SourceRange;

import java.util.List;
import java.util.Map;

/**
 * Typed paragraph block with inline content.
 */
public record ParagraphBlock(
        String id,
        List<InlineNode> inlines,
        SourceRange sourceRange,
        Map<String, Object> extraAttrs
) implements TypedBlockNode {

    public ParagraphBlock {
        inlines = TypedBlockSupport.list(inlines);
        extraAttrs = TypedBlockSupport.attrs(extraAttrs);
    }

    @Override
    public BlockType type() {
        return BlockType.PARAGRAPH;
    }

}
