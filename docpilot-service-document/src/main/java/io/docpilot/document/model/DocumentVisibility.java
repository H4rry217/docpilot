package io.docpilot.document.model;

/**
 * High-level document visibility.
 */
public enum DocumentVisibility {

    /**
     * Only explicit collaborators or owner can access.
     */
    PRIVATE,

    /**
     * Anyone with a future share link can read.
     */
    LINK_READ

}
