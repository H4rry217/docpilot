package io.docpilot.block.typed;

import io.docpilot.block.model.BlockType;
import io.docpilot.block.model.SourceRange;

import java.util.Map;

/**
 * Typed code block with language and text payload.
 */
public record CodeBlock(
        String id,
        String language,
        String text,
        SourceRange sourceRange,
        Map<String, Object> extraAttrs
) implements TypedBlockNode {

    public CodeBlock {
        language = language == null ? "" : language;
        text = text == null ? "" : text;
        extraAttrs = TypedBlockSupport.attrs(extraAttrs);
    }

    @Override
    public BlockType type() {
        return BlockType.CODE_BLOCK;
    }

}
