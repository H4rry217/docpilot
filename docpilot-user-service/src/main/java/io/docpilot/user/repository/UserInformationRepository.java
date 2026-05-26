package io.docpilot.user.repository;

import io.docpilot.user.model.UserInformation;
import io.docpilot.user.provider.UserInformationProvider;

/**
 * Storage boundary for users.
 */
public interface UserInformationRepository extends UserInformationProvider {

    /**
     * Creates or updates a user.
     */
    UserInformation save(UserInformation userInformation);

}
