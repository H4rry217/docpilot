package io.docpilot.user.application;

import io.docpilot.user.model.UserInformation;
import io.docpilot.user.repository.UserInformationRepository;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserInformationManagerTest {

    @Test
    void saveUserInformationNormalizesBasicFields() {
        UserInformationManager manager = new UserInformationManager(new InMemoryUserInformationRepository());
        UserInformation userInformation = new UserInformation();
        userInformation.setUserId(" u1 ");
        userInformation.setDisplayName(" Alice ");
        userInformation.setEmail(" alice@docpilot.local ");

        UserInformation saved = manager.saveUserInformation(userInformation);

        assertThat(saved.getUserId()).isEqualTo("u1");
        assertThat(saved.getDisplayName()).isEqualTo("Alice");
        assertThat(saved.getEmail()).isEqualTo("alice@docpilot.local");
        assertThat(manager.findByUserId("u1")).hasValue(saved);
    }

    @Test
    void rejectUserInformationWithoutUserId() {
        UserInformationManager manager = new UserInformationManager(new InMemoryUserInformationRepository());

        assertThatThrownBy(() -> manager.saveUserInformation(new UserInformation()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User id");
    }

    private static final class InMemoryUserInformationRepository implements UserInformationRepository {

        private final Map<String, UserInformation> userInformationById = new HashMap<>();

        @Override
        public UserInformation save(UserInformation userInformation) {
            userInformationById.put(userInformation.getUserId(), userInformation);
            return userInformation;
        }

        @Override
        public Optional<UserInformation> findByUserId(String userId) {
            return Optional.ofNullable(userInformationById.get(userId));
        }

    }

}
