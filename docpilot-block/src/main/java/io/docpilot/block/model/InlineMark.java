package io.docpilot.block.model;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * Mutable rich-text mark applied to an inline text range.
 */
@Getter
@Setter
public class InlineMark {

    /**
     * Semantic mark kind.
     */
    private MarkType type;

    /**
     * Mark-specific attributes such as href, title, color, or language.
     */
    private Map<String, Object> attrs = new HashMap<>();

    /**
     * Original Markdown source location for patch positioning.
     */
    private SourceRange sourceRange;

    public static InlineMark of(MarkType type) {
        return of(type, Map.of(), null);
    }

    public static InlineMark of(MarkType type, Map<String, Object> attrs) {
        return of(type, attrs, null);
    }

    public static InlineMark of(MarkType type, Map<String, Object> attrs, SourceRange sourceRange) {
        InlineMark mark = new InlineMark();
        mark.setType(type);
        mark.setAttrs(attrs == null ? new HashMap<>() : new HashMap<>(attrs));
        mark.setSourceRange(sourceRange);
        return mark;
    }

}
