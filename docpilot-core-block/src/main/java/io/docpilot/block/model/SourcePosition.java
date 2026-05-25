package io.docpilot.block.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Single source position in the original Markdown text.
 */
@Getter
@Setter
public class SourcePosition {

    /**
     * Zero-based character offset.
     */
    private int offset;

    /**
     * One-based line number.
     */
    private int line;

    /**
     * One-based column number.
     */
    private int column;

    public static SourcePosition of(int offset, int line, int column) {
        SourcePosition position = new SourcePosition();
        position.setOffset(offset);
        position.setLine(line);
        position.setColumn(column);
        return position;
    }

}
