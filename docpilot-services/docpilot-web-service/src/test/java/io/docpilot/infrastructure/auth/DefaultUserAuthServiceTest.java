package io.docpilot.infrastructure.auth;

import io.docpilot.common.auth.AuthSubject;
import io.docpilot.common.auth.AuthSubjectContext;
import io.docpilot.common.exception.ConflictException;
import io.docpilot.common.exception.ForbiddenException;
import io.docpilot.common.exception.UnauthorizedException;
import io.docpilot.common.web.auth.DocPilotJwtConfig;
import io.docpilot.user.model.UserInformation;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultUserAuthServiceTest {

    private final InMemoryAccountRepository repository = new InMemoryAccountRepository();
    private final PasswordHasher passwordHasher = new PasswordHasher("", 100000);
    private final DefaultUserAuthService authService = new DefaultUserAuthService(
            repository,
            passwordHasher,
            new DefaultJwtIssuer(new DocPilotJwtConfig()),
            true,
            AuthSubjectContext::currentSubject
    );

    @Test
    void registerNormalizesEmailAndDefaultsDisplayName() {
        UserInformation user = authService.register(" Alice@Example.COM ", "password123", null);

        assertThat(user.getEmail()).isEqualTo("alice@example.com");
        assertThat(user.getDisplayName()).isEqualTo("alice");
        assertThat(repository.findByEmail("alice@example.com")).isPresent();
    }

    @Test
    void registerRejectsDuplicateEmail() {
        authService.register("alice@example.com", "password123", "Alice");

        assertThatThrownBy(() -> authService.register(" ALICE@example.com ", "password123", "Alice"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void registerRejectsWhenRegistrationIsDisabled() {
        DefaultUserAuthService disabledAuthService = new DefaultUserAuthService(
                repository,
                passwordHasher,
                new DefaultJwtIssuer(new DocPilotJwtConfig()),
                false,
                AuthSubjectContext::currentSubject
        );

        assertThatThrownBy(() -> disabledAuthService.register("alice@example.com", "password123", "Alice"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Registration is disabled");
        assertThat(repository.findByEmail("alice@example.com")).isEmpty();
    }

    @Test
    void loginChecksPassword() {
        authService.register("alice@example.com", "password123", "Alice");

        assertThat(authService.login("alice@example.com", "password123").user().getDisplayName()).isEqualTo("Alice");
        assertThatThrownBy(() -> authService.login("alice@example.com", "bad-password"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void changePasswordAndDisplayNameUseCurrentSubject() {
        UserInformation user = authService.register("alice@example.com", "password123", "Alice");
        AuthSubject subject = new AuthSubject();
        subject.setUserId(user.getUserId());

        AuthSubjectContext.runAs(subject, () -> {
            authService.changePassword("password123", "changed123");
            assertThat(authService.login("alice@example.com", "changed123").user().getUserId())
                    .isEqualTo(subject.getUserId());

            assertThat(authService.changeDisplayName("Alice Changed").getDisplayName())
                    .isEqualTo("Alice Changed");
        });
    }

    private final class InMemoryAccountRepository implements DefaultUserAccountRepository {

        private long nextUserId = 1L;
        private final Map<Long, DefaultUserAccount> byUserId = new HashMap<>();
        private final Map<String, DefaultUserAccount> byEmail = new HashMap<>();

        @Override
        public DefaultUserAccount create(DefaultUserAccount account) {
            if (byEmail.containsKey(account.getEmail())) {
                throw new ConflictException("Email is already registered");
            }
            account.setUserId(nextUserId++);
            byUserId.put(account.getUserId(), account);
            byEmail.put(account.getEmail(), account);
            return account;
        }

        @Override
        public Optional<DefaultUserAccount> findByEmail(String email) {
            return Optional.ofNullable(byEmail.get(email));
        }

        @Override
        public Optional<DefaultUserAccount> findAccountByUserId(Long userId) {
            return Optional.ofNullable(byUserId.get(userId));
        }

        @Override
        public DefaultUserAccount updatePasswordHash(Long userId, String passwordHash) {
            DefaultUserAccount account = byUserId.get(userId);
            account.setPasswordHash(passwordHash);
            return account;
        }

        @Override
        public DefaultUserAccount updateDisplayName(Long userId, String displayName) {
            DefaultUserAccount account = byUserId.get(userId);
            account.setDisplayName(displayName);
            return account;
        }

    }

}
