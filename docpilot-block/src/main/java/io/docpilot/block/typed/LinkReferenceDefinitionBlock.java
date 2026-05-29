package io.docpilot.block.typed;

import io.docpilot.block.model.BlockType;
import io.docpilot.block.model.SourceRange;

import java.util.Map;

/**
 * Typed link reference definition block.
 */
public record LinkReferenceDefinitionBlock(
        String id,
        String label,
        String href,
        String title,
        String raw,
        SourceRange sourceRange,
        Map<String, Object> extraAttrs
) implements TypedBlockNode {

    public LinkReferenceDefinitionBlock {
        label = label == null ? "" : label;
        href = href == null ? "" : href;
        title = title == null ? "" : title;
        raw = raw == null ? "" : raw;
        extraAttrs = TypedBlockSupport.attrs(extraAttrs);
    }

    @Override
    public BlockType type() {
        return BlockType.LINK_REFERENCE_DEFINITION;
    }

}
