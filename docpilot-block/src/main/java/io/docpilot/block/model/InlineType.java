package io.docpilot.block.model;

public enum InlineType {

    /** Plain text. */
    TEXT("text"),
    /** Markdown soft line break. */
    SOFT_BREAK("softBreak"),
    /** Markdown hard line break. */
    HARD_BREAK("hardBreak"),
    /** Inline code span. */
    CODE("code"),
    /** Link with href/title attrs. */
    LINK("link"),
    /** Image with src/alt/title attrs. */
    IMAGE("image"),
    /** Raw inline HTML preserved as data. */
    HTML_INLINE("htmlInline"),
    /** Fallback for parser inline nodes not yet modeled. */
    UNSUPPORTED_INLINE("unsupportedInline");

    private final String value;

    InlineType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

}
