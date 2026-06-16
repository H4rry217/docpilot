package io.docpilot.infrastructure.auth.provider;

import io.docpilot.user.model.UserInformation;

/**
 * Optional capability interface for providers that implement DocPilot-managed
 * email/password account flows.
 */
public interface PasswordLoginAuthProvider {

    AuthSession login(LoginCommand command);

    UserInformation register(RegisterCommand command);

    void changePassword(ChangePasswordCommand command);

}
