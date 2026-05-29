package io.docpilot.block.typed;

import io.docpilot.block.model.BlockType;
import io.docpilot.block.model.InlineNode;
import io.docpilot.block.model.SourceRange;

import java.util.List;
import java.util.Map;

/**
 * Generic typed view for block types that do not need specialized attrs yet.
 */
public record GenericTypedBlock(
        String id,
        BlockType type,
        List<InlineNode> inlines,
        List<TypedBlockNode> children,
        SourceRange sourceRange,
        Map<String, Object> extraAttrs
) implements TypedBlockNode {

    public GenericTypedBlock {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        inlines = TypedBlockSupport.list(inlines);
        children = TypedBlockSupport.list(children);
        extraAttrs = TypedBlockSupport.attrs(extraAttrs);
    }

}
