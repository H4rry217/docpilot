package io.docpilot.document.auth;

import java.util.Optional;

/**
 * Boundary for reading the current authenticated subject.
 */
public interface AuthContextProvider {

    /**
     * Returns the current subject, or empty when the request is anonymous.
     */
    Optional<AuthSubject> currentSubject();

}
