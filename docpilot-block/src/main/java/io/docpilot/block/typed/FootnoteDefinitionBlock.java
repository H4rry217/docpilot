package io.docpilot.block.typed;

import io.docpilot.block.model.BlockType;
import io.docpilot.block.model.SourceRange;

import java.util.List;
import java.util.Map;

/**
 * Typed footnote definition block.
 */
public record FootnoteDefinitionBlock(
        String id,
        String label,
        String raw,
        List<TypedBlockNode> children,
        SourceRange sourceRange,
        Map<String, Object> extraAttrs
) implements TypedBlockNode {

    public FootnoteDefinitionBlock {
        label = label == null ? "" : label;
        raw = raw == null ? "" : raw;
        children = TypedBlockSupport.list(children);
        extraAttrs = TypedBlockSupport.attrs(extraAttrs);
    }

    @Override
    public BlockType type() {
        return BlockType.FOOTNOTE_DEFINITION;
    }

}
