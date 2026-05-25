package io.docpilot.block.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mutable inline node carried inside text-like blocks.
 */
@Getter
@Setter
public class InlineNode {

    /**
     * Semantic inline kind.
     */
    private InlineType type;

    /**
     * Inline text payload or display text.
     */
    private String text = "";

    /**
     * Inline-specific attributes such as href, src, title, or raw HTML.
     */
    private Map<String, Object> attrs = new HashMap<>();

    /**
     * Ordered text marks applied to this inline node.
     */
    private List<MarkType> marks = new ArrayList<>();

    /**
     * Original Markdown source location for patch positioning.
     */
    private SourceRange sourceRange;

    public static InlineNode of(InlineType type, String text, Map<String, Object> attrs,
                                List<MarkType> marks, SourceRange sourceRange) {
        InlineNode inline = new InlineNode();
        inline.setType(type);
        inline.setText(text == null ? "" : text);
        inline.setAttrs(attrs == null ? new HashMap<>() : new HashMap<>(attrs));
        inline.setMarks(marks == null ? new ArrayList<>() : new ArrayList<>(marks));
        inline.setSourceRange(sourceRange);
        return inline;
    }

}
