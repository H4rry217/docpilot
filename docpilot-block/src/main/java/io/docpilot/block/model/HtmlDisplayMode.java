package io.docpilot.block.model;

import lombok.Getter;

/**
 * Rendering layout mode for preserved HTML blocks.
 */
@Getter
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

}
