package io.docpilot.document.user;

import java.util.Optional;

/**
 * Boundary for reading user profile data.
 */
public interface UserProfileProvider {

    /**
     * Finds a profile by user id.
     */
    Optional<UserProfile> findByUserId(String userId);

}
