package io.docpilot.block.model;

public enum MarkType {

    /** Strong emphasis. */
    BOLD("bold"),
    /** Emphasis. */
    ITALIC("italic"),
    /** GitHub-flavored strikethrough. */
    STRIKE("strike"),
    /** Inline code span. */
    CODE("code"),
    /** Link with href/title attrs. */
    LINK("link"),
    /** Underlined text. */
    UNDERLINE("underline"),
    /** Inserted text. */
    INSERT("insert"),
    /** Subscript text. */
    SUBSCRIPT("subscript"),
    /** Superscript text. */
    SUPERSCRIPT("superscript"),
    /** Highlighted text. */
    HIGHLIGHT("highlight");

    private final String value;

    MarkType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

}
