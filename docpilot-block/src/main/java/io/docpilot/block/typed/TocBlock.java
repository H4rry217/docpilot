package io.docpilot.block.typed;

import io.docpilot.block.model.BlockType;
import io.docpilot.block.model.SourceRange;

import java.util.Map;

/**
 * Typed table-of-contents marker block.
 */
public record TocBlock(
        String id,
        String raw,
        SourceRange sourceRange,
        Map<String, Object> extraAttrs
) implements TypedBlockNode {

    public TocBlock {
        raw = raw == null || raw.isBlank() ? "[TOC]" : raw;
        extraAttrs = TypedBlockSupport.attrs(extraAttrs);
    }

    @Override
    public BlockType type() {
        return BlockType.TOC;
    }

}
