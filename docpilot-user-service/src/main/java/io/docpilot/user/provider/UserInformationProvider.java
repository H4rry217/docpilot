package io.docpilot.user.provider;

import io.docpilot.user.model.UserInformation;

import java.util.Optional;

/**
 * Boundary for reading user data.
 */
public interface UserInformationProvider {

    /**
     * Finds a user by id.
     */
    Optional<UserInformation> findByUserId(String userId);

}
