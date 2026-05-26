package io.docpilot.document.auth;

import io.docpilot.document.model.DocPilotDocument;
import io.docpilot.document.model.DocumentAction;
import io.docpilot.user.auth.AuthSubject;

/**
 * Boundary for document-level authorization decisions.
 */
public interface DocumentAccessAuthorizer {

    /**
     * Returns true when the subject can perform the action on the document.
     */
    boolean canAccess(AuthSubject subject, DocPilotDocument document, DocumentAction action);

}
