package io.docpilot.block.typed;

import java.util.Locale;

/**
 * Supported alignment values for typed table cells.
 */
public enum TableCellAlignment {

    NONE("none"),
    LEFT("left"),
    CENTER("center"),
    RIGHT("right");

    private final String value;

    TableCellAlignment(String value) {
        this.value = value;
    }

    /**
     * Returns the canonical JSON value for this alignment.
     */
    public String value() {
        return value;
    }

    /**
     * Parses a canonical alignment value, defaulting to NONE.
     */
    public static TableCellAlignment from(String value) {
        if (value == null) {
            return NONE;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "left" -> LEFT;
            case "center" -> CENTER;
            case "right" -> RIGHT;
            default -> NONE;
        };
    }

}
