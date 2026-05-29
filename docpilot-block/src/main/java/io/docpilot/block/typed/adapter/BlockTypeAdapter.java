package io.docpilot.block.typed.adapter;

import io.docpilot.block.model.BlockNode;
import io.docpilot.block.model.BlockType;
import io.docpilot.block.typed.BlockNodeConverter;
import io.docpilot.block.typed.TypedBlockNode;
import io.docpilot.block.typed.ValidationIssue;

import java.util.List;

/**
 * Adapter that owns conversion, normalization, and validation for one block type.
 */
public interface BlockTypeAdapter<T extends TypedBlockNode> {

    BlockType type();

    T fromBlockNode(BlockNode node, BlockNodeConverter converter);

    BlockNode toBlockNode(T node, BlockNodeConverter converter);

    List<ValidationIssue> validate(BlockNode node, String path);

}
