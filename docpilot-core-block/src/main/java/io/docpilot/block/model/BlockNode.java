package io.docpilot.block.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mutable block node in the DocPilot document tree.
 */
@Getter
@Setter
public class BlockNode {

    /**
     * Global block identifier, generated as UUID without hyphens.
     */
    private String id;

    /**
     * Semantic block kind.
     */
    private BlockType type;

    /**
     * Block-specific attributes such as heading level or HTML source.
     */
    private Map<String, Object> attrs = new HashMap<>();

    /**
     * Inline content owned directly by this block.
     */
    private List<InlineNode> inlines = new ArrayList<>();

    /**
     * Nested block content, used by lists, quotes, and tables.
     */
    private List<BlockNode> children = new ArrayList<>();

    /**
     * Original Markdown source location for patch positioning.
     */
    private SourceRange sourceRange;

    public static BlockNode of(String id, BlockType type, Map<String, Object> attrs,
                               List<InlineNode> inlines, List<BlockNode> children,
                               SourceRange sourceRange) {
        BlockNode block = new BlockNode();
        block.setId(id);
        block.setType(type);
        block.setAttrs(attrs == null ? new HashMap<>() : new HashMap<>(attrs));
        block.setInlines(inlines == null ? new ArrayList<>() : new ArrayList<>(inlines));
        block.setChildren(children == null ? new ArrayList<>() : new ArrayList<>(children));
        block.setSourceRange(sourceRange);
        return block;
    }

}
