package io.docpilot.block.typed;

/**
 * Canonical attr keys used by DocPilot block nodes.
 */
public enum BlockAttrs {

    ID("id"),
    LEVEL("level"),
    START("start"),
    CHECKED("checked"),
    LANGUAGE("language"),
    TEXT("text"),
    SOURCE("source"),
    RAW("raw"),
    NODE_TYPE("nodeType"),
    TITLE("title"),
    DISPLAY_MODE("displayMode"),
    FIXED_HEIGHT_PX("fixedHeightPx"),
    ALLOW_SCRIPTS("allowScripts"),
    HEADER("header"),
    ALIGNMENT("alignment"),
    KIND("kind"),
    COLLAPSIBLE("collapsible"),
    OPEN("open"),
    LABEL("label"),
    HREF("href"),
    FORMAT("format"),
    DATA("data"),
    NOTATION("notation"),
    DELIMITER("delimiter"),
    ENGINE("engine"),
    HTML_ID("htmlId"),
    CLASS_NAMES("classNames"),
    DATA_ATTRS("dataAttrs");

    private final String key;

    BlockAttrs(String key) {
        this.key = key;
    }

    /**
     * Returns the JSON/canonical map key represented by this enum value.
     */
    public String key() {
        return key;
    }

}
