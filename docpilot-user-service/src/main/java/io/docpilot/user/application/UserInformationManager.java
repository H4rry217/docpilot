package io.docpilot.user.application;

import io.docpilot.user.model.UserInformation;
import io.docpilot.user.provider.UserInformationProvider;
import io.docpilot.user.repository.UserInformationRepository;

import java.util.Optional;

/**
 * Application boundary for user operations.
 */
public class UserInformationManager implements UserInformationProvider {

    private final UserInformationRepository userInformationRepository;

    public UserInformationManager(UserInformationRepository userInformationRepository) {
        this.userInformationRepository = userInformationRepository;
    }

    @Override
    public Optional<UserInformation> findByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        return userInformationRepository.findByUserId(userId);
    }

    public UserInformation saveUserInformation(UserInformation userInformation) {
        if (userInformation == null || userInformation.getUserId() == null || userInformation.getUserId().isBlank()) {
            throw new IllegalArgumentException("User id is required");
        }
        userInformation.setUserId(userInformation.getUserId().strip());
        if (userInformation.getDisplayName() != null) {
            userInformation.setDisplayName(userInformation.getDisplayName().strip());
        }
        if (userInformation.getEmail() != null) {
            userInformation.setEmail(userInformation.getEmail().strip());
        }
        return userInformationRepository.save(userInformation);
    }

}
