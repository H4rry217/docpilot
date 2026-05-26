package io.docpilot.config;

import io.docpilot.user.application.UserInformationManager;
import io.docpilot.user.model.UserInformation;
import io.docpilot.user.repository.UserInformationRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class UserApplicationConfig {

    public static final String DEV_USER_ID = "dev-user";
    public static final String DEV_DISPLAY_NAME = "HarryZ";

    @Bean
    public UserInformationRepository userInformationRepository() {
        InMemoryUserInformationRepository repository = new InMemoryUserInformationRepository();
        UserInformation userInformation = new UserInformation();
        userInformation.setUserId(DEV_USER_ID);
        userInformation.setDisplayName(DEV_DISPLAY_NAME);
        userInformation.setEmail("dev-user@docpilot.local");
        repository.save(userInformation);
        return repository;
    }

    @Bean
    public UserInformationManager userInformationProvider(UserInformationRepository userInformationRepository) {
        return new UserInformationManager(userInformationRepository);
    }

    private static final class InMemoryUserInformationRepository implements UserInformationRepository {

        private final Map<String, UserInformation> userInformationById = new ConcurrentHashMap<>();

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
