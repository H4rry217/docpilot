package io.docpilot.document.auth;

import io.docpilot.document.model.DocPilotDocument;
import io.docpilot.document.model.DocumentAction;

/**
 * Boundary for document-level authorization decisions.
 */
public interface DocumentAccessAuthorizer {

    /**
     * Returns true when the subject can perform the action on the document.
     */
    boolean canAccess(AuthSubject subject, DocPilotDocument document, DocumentAction action);

}
