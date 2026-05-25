package io.docpilot.document.model;

/**
 * Document lifecycle state.
 */
public enum DocumentState {

    /**
     * Normal editable document.
     */
    ACTIVE,

    /**
     * Soft-deleted document.
     */
    DELETED

}
