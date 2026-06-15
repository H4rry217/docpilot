package io.docpilot.workspace.inlinecompletion;

/**
 * Shape of the markdown fragment returned for inline completion.
 */
public enum InlineCompletionShape {

    SHORT,
    SENTENCE,
    PARAGRAPH,
    LIST_ITEM,
    TABLE_CELL,
    CODE_LINE

}
