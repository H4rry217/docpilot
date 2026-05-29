package io.docpilot.block.typed;

import io.docpilot.block.model.BlockType;
import io.docpilot.block.model.SourceRange;

import java.util.Map;

/**
 * Typed block-level math expression.
 */
public record MathBlock(
        String id,
        String notation,
        String text,
        String delimiter,
        SourceRange sourceRange,
        Map<String, Object> extraAttrs
) implements TypedBlockNode {

    public MathBlock {
        notation = notation == null || notation.isBlank() ? "latex" : notation;
        text = text == null ? "" : text;
        delimiter = delimiter == null || delimiter.isBlank() ? "$$" : delimiter;
        extraAttrs = TypedBlockSupport.attrs(extraAttrs);
    }

    @Override
    public BlockType type() {
        return BlockType.MATH_BLOCK;
    }

}
