package io.docpilot.common.auth;

import java.util.Optional;

/**
 * Boundary for reading the current authenticated subject.
 */
public interface AuthContextProvider {

    /**
     * Returns the current subject, or empty when the current flow is anonymous.
     */
    Optional<AuthSubject> currentSubject();

}
