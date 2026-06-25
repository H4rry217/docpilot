package io.docpilot.workspace.event;

/**
 * Published after document content is successfully committed as a new revision.
 */
public record DocumentContentChangedEvent(
        /**
         * User id that owns the workspace containing the document.
         */
        Long ownerUserId,

        /**
         * Workspace id where the document belongs.
         */
        Long workspaceId,

        /**
         * Document id whose content has changed.
         */
        Long documentId,

        /**
         * Newly committed revision id.
         */
        Long revisionId,

        /**
         * Version number of the newly committed revision.
         */
        Long version,

        /**
         * Client-visible version that the save request was based on.
         */
        Long baseVersion,

        /**
         * Optional client mutation id used for idempotent save retries.
         */
        String clientMutationId
) {
}
