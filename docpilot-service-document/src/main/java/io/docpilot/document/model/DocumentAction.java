package io.docpilot.document.model;

/**
 * Protected operation on a document.
 */
public enum DocumentAction {

    /**
     * Read document metadata and content.
     */
    READ,

    /**
     * Modify document title, content, or metadata.
     */
    WRITE,

    /**
     * Soft-delete or restore a document.
     */
    DELETE,

    /**
     * Manage document collaborators and sharing settings.
     */
    MANAGE

}
