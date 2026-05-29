package io.docpilot.block.typed;

import io.docpilot.block.model.BlockType;
import io.docpilot.block.model.SourceRange;

import java.util.List;
import java.util.Map;

/**
 * Typed fallback for raw extension or unsupported blocks.
 */
public record RawBlock(
        String id,
        BlockType type,
        String source,
        String raw,
        String nodeType,
        List<TypedBlockNode> children,
        SourceRange sourceRange,
        Map<String, Object> extraAttrs
) implements TypedBlockNode {

    public RawBlock {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        source = source == null ? "" : source;
        raw = raw == null ? "" : raw;
        nodeType = nodeType == null ? "" : nodeType;
        children = TypedBlockSupport.list(children);
        extraAttrs = TypedBlockSupport.attrs(extraAttrs);
    }

}
