package io.docpilot.block.typed;

import io.docpilot.block.model.BlockDocument;
import io.docpilot.block.model.BlockNode;
import io.docpilot.block.model.BlockType;
import io.docpilot.block.typed.adapter.BlockTypeAdapter;
import io.docpilot.block.typed.adapter.BlockTypeAdapters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Converts between the canonical map-based block model and the typed runtime view.
 */
public class BlockNodeConverter {

    private static final BlockNodeConverter DEFAULT = new BlockNodeConverter();

    private final Map<BlockType, BlockTypeAdapter<? extends TypedBlockNode>> adapters;

    public BlockNodeConverter() {
        this.adapters = Collections.unmodifiableMap(new EnumMap<>(BlockTypeAdapters.createDefaultAdapters()));
    }

    public static TypedBlockDocument toTyped(BlockDocument document) {
        return DEFAULT.convertDocument(document);
    }

    public static TypedBlockNode toTyped(BlockNode node) {
        return DEFAULT.convertNode(node);
    }

    public static BlockDocument toBlockDocument(TypedBlockDocument document) {
        return DEFAULT.convertDocument(document);
    }

    public static BlockNode toBlockNode(TypedBlockNode node) {
        return DEFAULT.convertNode(node);
    }

    public static List<ValidationIssue> validate(BlockDocument document) {
        return DEFAULT.validateDocument(document);
    }

    public List<TypedBlockNode> toTypedNodes(List<BlockNode> nodes) {
        return nodes == null ? List.of() : nodes.stream().map(this::convertNode).toList();
    }

    public List<BlockNode> toBlockNodes(List<TypedBlockNode> nodes) {
        return nodes == null ? List.of() : nodes.stream().map(this::convertNode).toList();
    }

    private TypedBlockDocument convertDocument(BlockDocument document) {
        if (document == null) {
            return new TypedBlockDocument(List.of(), Map.of());
        }
        return new TypedBlockDocument(toTypedNodes(document.getBlocks()), document.getMetadata());
    }

    private BlockDocument convertDocument(TypedBlockDocument document) {
        BlockDocument next = new BlockDocument();
        if (document == null) {
            return next;
        }
        next.setBlocks(toBlockNodes(document.blocks()));
        next.setMetadata(TypedBlockSupport.mutableAttrs(document.metadata()));
        return next;
    }

    @SuppressWarnings("unchecked")
    private TypedBlockNode convertNode(BlockNode node) {
        if (node == null || node.getType() == null) {
            return null;
        }
        BlockTypeAdapter<TypedBlockNode> adapter = (BlockTypeAdapter<TypedBlockNode>) adapter(node.getType());
        return adapter.fromBlockNode(node, this);
    }

    @SuppressWarnings("unchecked")
    private BlockNode convertNode(TypedBlockNode node) {
        if (node == null) {
            return null;
        }
        BlockTypeAdapter<TypedBlockNode> adapter = (BlockTypeAdapter<TypedBlockNode>) adapter(node.type());
        return adapter.toBlockNode(node, this);
    }

    private List<ValidationIssue> validateDocument(BlockDocument document) {
        if (document == null) {
            return List.of();
        }
        List<ValidationIssue> issues = new ArrayList<>();
        validateNodes(document.getBlocks(), "blocks", issues);
        return issues;
    }

    private void validateNodes(List<BlockNode> nodes, String path, List<ValidationIssue> issues) {
        if (nodes == null) {
            return;
        }
        for (int i = 0; i < nodes.size(); i++) {
            BlockNode node = nodes.get(i);
            if (node == null || node.getType() == null) {
                continue;
            }
            String nodePath = path + "[" + i + "]";
            issues.addAll(adapter(node.getType()).validate(node, nodePath));
            validateNodes(node.getChildren(), nodePath + ".children", issues);
        }
    }

    private BlockTypeAdapter<? extends TypedBlockNode> adapter(BlockType type) {
        BlockTypeAdapter<? extends TypedBlockNode> adapter = adapters.get(type);
        if (adapter == null) {
            throw new IllegalArgumentException("No block type adapter registered for " + type);
        }
        return adapter;
    }

}
