package io.docpilot.block.typed;

import io.docpilot.block.model.BlockType;
import io.docpilot.block.model.SourceRange;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Typed callout/admonition container.
 */
public record CalloutBlock(
        String id,
        String kind,
        String title,
        boolean collapsible,
        boolean open,
        List<TypedBlockNode> children,
        SourceRange sourceRange,
        Map<String, Object> extraAttrs
) implements TypedBlockNode {

    public CalloutBlock {
        kind = kind == null || kind.isBlank() ? "note" : kind.toLowerCase(Locale.ROOT);
        title = title == null ? "" : title;
        children = TypedBlockSupport.list(children);
        extraAttrs = TypedBlockSupport.attrs(extraAttrs);
    }

    @Override
    public BlockType type() {
        return BlockType.CALLOUT;
    }

}
