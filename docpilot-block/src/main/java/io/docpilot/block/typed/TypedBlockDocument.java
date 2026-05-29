package io.docpilot.block.typed;

import java.util.List;
import java.util.Map;

/**
 * Runtime typed view of a DocPilot block document.
 */
public record TypedBlockDocument(
        List<TypedBlockNode> blocks,
        Map<String, Object> metadata
) {

    public TypedBlockDocument {
        blocks = TypedBlockSupport.list(blocks);
        metadata = TypedBlockSupport.attrs(metadata);
    }

}
