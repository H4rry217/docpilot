package io.docpilot.block.typed;

import io.docpilot.block.model.BlockType;
import io.docpilot.block.model.SourceRange;

import java.util.Map;

/**
 * Typed front matter block preserving raw and parsed metadata.
 */
public record FrontMatterBlock(
        String id,
        String format,
        String raw,
        Object data,
        SourceRange sourceRange,
        Map<String, Object> extraAttrs
) implements TypedBlockNode {

    public FrontMatterBlock {
        format = format == null || format.isBlank() ? "yaml" : format;
        raw = raw == null ? "" : raw;
        extraAttrs = TypedBlockSupport.attrs(extraAttrs);
    }

    @Override
    public BlockType type() {
        return BlockType.FRONT_MATTER;
    }

}
