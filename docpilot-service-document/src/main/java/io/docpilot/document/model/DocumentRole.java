package io.docpilot.document.model;

/**
 * Role a subject can have on a document.
 */
public enum DocumentRole {

    /**
     * Full control over document metadata and content.
     */
    OWNER,

    /**
     * Can read and modify document content.
     */
    EDITOR,

    /**
     * Can only read document content.
     */
    VIEWER

}
