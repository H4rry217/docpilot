package io.docpilot.block.typed;

import io.docpilot.block.model.BlockType;

/**
 * Describes a validation issue found in a canonical block node.
 */
public record ValidationIssue(
        String path,
        BlockType blockType,
        String attrKey,
        ValidationSeverity severity,
        String message
) {
}
