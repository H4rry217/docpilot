package io.docpilot.block.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Inclusive-start, exclusive-end range in the original Markdown text.
 */
@Getter
@Setter
public class SourceRange {

    /**
     * Start position of a parsed node.
     */
    private SourcePosition start;

    /**
     * End position of a parsed node.
     */
    private SourcePosition end;

    public static SourceRange of(SourcePosition start, SourcePosition end) {
        SourceRange range = new SourceRange();
        range.setStart(start);
        range.setEnd(end);
        return range;
    }

}
