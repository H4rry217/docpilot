package io.docpilot.block.model;

/**
 * Rendering layout mode for preserved HTML blocks.
 */
public enum HtmlDisplayMode {

    /**
     * Render inside a fixed-height frame.
     */
    FIXED("fixed"),

    /**
     * Resize the frame to the HTML document height when the frontend can measure it.
     */
    AUTO("auto"),

    /**
     * Legacy value kept for existing snapshots. Frontends should treat it as fixed.
     */
    FIT("fit");

    private final String value;

    HtmlDisplayMode(String value) {
        this.value = value;
    }

    /**
     * Stable value used in ProseMirror JSON attrs.
     */
    public String getValue() {
        return value;
    }

}
