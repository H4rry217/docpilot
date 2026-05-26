package io.docpilot.block.model;

public enum BlockType {

    /** Document root when a nested model needs an explicit root block. */
    DOCUMENT("document"),
    /** Plain paragraph block. */
    PARAGRAPH("paragraph"),
    /** Heading block with numeric level attr. */
    HEADING("heading"),
    /** Block quote container. */
    BLOCK_QUOTE("blockquote"),
    /** Unordered list container. */
    BULLET_LIST("bulletList"),
    /** Ordered list container. */
    ORDERED_LIST("orderedList"),
    /** Regular list item container. */
    LIST_ITEM("listItem"),
    /** GitHub-flavored task list item. */
    TASK_LIST_ITEM("taskListItem"),
    /** Fenced or indented code block. */
    CODE_BLOCK("codeBlock"),
    /** Horizontal rule. */
    THEMATIC_BREAK("thematicBreak"),
    /** Table container. */
    TABLE("table"),
    /** Table row container. */
    TABLE_ROW("tableRow"),
    /** Table cell with header/alignment attrs. */
    TABLE_CELL("tableCell"),
    /** Raw HTML block preserved as data. */
    HTML_BLOCK("htmlBlock"),
    /** Fallback for parser nodes not yet modeled. */
    UNSUPPORTED_BLOCK("unsupportedBlock");

    private final String value;

    BlockType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

}
