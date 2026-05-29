package io.docpilot.block.typed;

import io.docpilot.block.model.BlockType;
import io.docpilot.block.model.SourceRange;

import java.util.List;
import java.util.Map;

/**
 * Typed GitHub-flavored task list item.
 */
public record TaskListItemBlock(
        String id,
        boolean checked,
        List<TypedBlockNode> children,
        SourceRange sourceRange,
        Map<String, Object> extraAttrs
) implements TypedBlockNode {

    public TaskListItemBlock {
        children = TypedBlockSupport.list(children);
        extraAttrs = TypedBlockSupport.attrs(extraAttrs);
    }

    @Override
    public BlockType type() {
        return BlockType.TASK_LIST_ITEM;
    }

}
