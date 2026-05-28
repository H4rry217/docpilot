package io.docpilot.block.model;

import lombok.Getter;

@Getter
public enum InlineType {

    /** Plain text. */
    TEXT("text"),
    /** Markdown soft line break. */
    SOFT_BREAK("softBreak"),
    /** Markdown hard line break. */
    HARD_BREAK("hardBreak"),
    /** Image with src/alt/title attrs. */
    IMAGE("image"),
    /** Inline math expression. */
    MATH_INLINE("mathInline"),
    /** Footnote reference. */
    FOOTNOTE_REF("footnoteRef"),
    /** Emoji shortcut or unicode emoji token. */
    EMOJI("emoji"),
    /** Raw inline HTML preserved as data. */
    HTML_INLINE("htmlInline"),
    /** Fallback for known extension inlines not yet modeled in detail. */
    EXTENSION_INLINE("extensionInline"),
    /** Fallback for parser inline nodes not yet modeled. */
    UNSUPPORTED_INLINE("unsupportedInline");

    private final String value;

    InlineType(String value) {
        this.value = value;
    }

}
