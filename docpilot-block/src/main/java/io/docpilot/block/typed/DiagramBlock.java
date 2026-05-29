package io.docpilot.block.typed;

import io.docpilot.block.model.BlockType;
import io.docpilot.block.model.SourceRange;

import java.util.Map;

/**
 * Typed diagram source block such as Mermaid.
 */
public record DiagramBlock(
        String id,
        String engine,
        String text,
        SourceRange sourceRange,
        Map<String, Object> extraAttrs
) implements TypedBlockNode {

    public DiagramBlock {
        engine = engine == null || engine.isBlank() ? "mermaid" : engine;
        text = text == null ? "" : text;
        extraAttrs = TypedBlockSupport.attrs(extraAttrs);
    }

    @Override
    public BlockType type() {
        return BlockType.DIAGRAM_BLOCK;
    }

}
