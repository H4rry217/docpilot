package io.docpilot.block.model;

public enum MarkType {

    /** Strong emphasis. */
    BOLD("bold"),
    /** Emphasis. */
    ITALIC("italic"),
    /** GitHub-flavored strikethrough. */
    STRIKE("strike");

    private final String value;

    MarkType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

}
