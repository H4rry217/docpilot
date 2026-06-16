package io.docpilot.infrastructure.auth.provider;

import io.docpilot.user.model.UserInformation;

/**
 * Optional capability interface for providers that allow DocPilot to update
 * profile fields.
 */
public interface UserProfileAuthProvider {

    UserInformation changeDisplayName(ChangeDisplayNameCommand command);

}
