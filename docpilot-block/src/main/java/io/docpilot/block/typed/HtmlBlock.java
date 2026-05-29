package io.docpilot.block.typed;

import io.docpilot.block.model.BlockType;
import io.docpilot.block.model.HtmlDisplayMode;
import io.docpilot.block.model.SourceRange;

import java.util.Map;

/**
 * Typed preserved HTML block with normalized preview options.
 */
public record HtmlBlock(
        String id,
        String title,
        String source,
        HtmlDisplayMode displayMode,
        int fixedHeightPx,
        boolean allowScripts,
        SourceRange sourceRange,
        Map<String, Object> extraAttrs
) implements TypedBlockNode {

    public HtmlBlock {
        title = title == null ? "HTML" : title;
        source = source == null ? "" : source;
        displayMode = displayMode == null || displayMode == HtmlDisplayMode.FIT ? HtmlDisplayMode.FIXED : displayMode;
        fixedHeightPx = fixedHeightPx < 120 || fixedHeightPx > 1600 ? 320 : fixedHeightPx;
        extraAttrs = TypedBlockSupport.attrs(extraAttrs);
    }

    @Override
    public BlockType type() {
        return BlockType.HTML_BLOCK;
    }

}
