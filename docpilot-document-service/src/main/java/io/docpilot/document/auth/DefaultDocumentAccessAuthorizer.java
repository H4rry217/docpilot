package io.docpilot.document.auth;

import io.docpilot.common.auth.AuthSubject;
import io.docpilot.document.model.DocPilotDocument;
import io.docpilot.document.model.DocumentAction;
import io.docpilot.document.model.DocumentVisibility;

import java.util.Objects;

/**
 * Default authorization policy before collaborator storage exists.
 */
public class DefaultDocumentAccessAuthorizer implements DocumentAccessAuthorizer {

    public static final String ROLE_ADMIN = "admin";

    @Override
    public boolean canAccess(AuthSubject subject, DocPilotDocument document, DocumentAction action) {
        if (document == null || action == null) {
            return false;
        }

        if (action == DocumentAction.READ && document.getVisibility() == DocumentVisibility.LINK_READ) {
            return true;
        }

        if (subject == null) {
            return false;
        }

        if (subject.hasRole(ROLE_ADMIN)) {
            return true;
        }

        return Objects.equals(subject.getUserId(), document.getOwnerUserId());
    }

}
