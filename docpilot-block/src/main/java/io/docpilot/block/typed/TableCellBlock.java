package io.docpilot.block.typed;

import io.docpilot.block.model.BlockType;
import io.docpilot.block.model.InlineNode;
import io.docpilot.block.model.SourceRange;

import java.util.List;
import java.util.Map;

/**
 * Typed table cell with header and alignment metadata.
 */
public record TableCellBlock(
        String id,
        boolean header,
        TableCellAlignment alignment,
        List<InlineNode> inlines,
        SourceRange sourceRange,
        Map<String, Object> extraAttrs
) implements TypedBlockNode {

    public TableCellBlock {
        alignment = alignment == null ? TableCellAlignment.NONE : alignment;
        inlines = TypedBlockSupport.list(inlines);
        extraAttrs = TypedBlockSupport.attrs(extraAttrs);
    }

    @Override
    public BlockType type() {
        return BlockType.TABLE_CELL;
    }

}
