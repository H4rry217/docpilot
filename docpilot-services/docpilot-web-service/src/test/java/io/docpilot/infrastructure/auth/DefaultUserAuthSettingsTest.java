package io.docpilot.infrastructure.auth;

import io.docpilot.common.exception.ConflictException;
import io.docpilot.system.repository.SystemSettingRepository;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultUserAuthSettingsTest {

    @Test
    void createsAndReusesGeneratedPasswordPepper() {
        InMemorySystemSettingRepository systemSettings = new InMemorySystemSettingRepository();
        InMemoryAccountRepository accounts = new InMemoryAccountRepository();
        DefaultUserAuthSettings settings = new DefaultUserAuthSettings(systemSettings, accounts);

        String firstPepper = settings.passwordPepper("");
        String sameSettingsPepper = settings.passwordPepper(null);
        String secondSettingsPepper = new DefaultUserAuthSettings(systemSettings, accounts).passwordPepper("");

        assertThat(firstPepper).isNotBlank();
        assertThat(sameSettingsPepper).isEqualTo(firstPepper);
        assertThat(secondSettingsPepper).isEqualTo(firstPepper);
    }

    @Test
    void configuredPasswordPepperWins() {
        DefaultUserAuthSettings settings = new DefaultUserAuthSettings(
                new InMemorySystemSettingRepository(),
                new InMemoryAccountRepository()
        );

        assertThat(settings.passwordPepper("configured-pepper")).isEqualTo("configured-pepper");
    }

    @Test
    void createsAndReusesGeneratedJwtSecret() {
        InMemorySystemSettingRepository systemSettings = new InMemorySystemSettingRepository();
        InMemoryAccountRepository accounts = new InMemoryAccountRepository();
        DefaultUserAuthSettings settings = new DefaultUserAuthSettings(systemSettings, accounts);

        String firstSecret = settings.jwtSecret("");
        String sameSettingsSecret = settings.jwtSecret(null);
        String secondSettingsSecret = new DefaultUserAuthSettings(systemSettings, accounts).jwtSecret("");

        assertThat(firstSecret).isNotBlank();
        assertThat(sameSettingsSecret).isEqualTo(firstSecret);
        assertThat(secondSettingsSecret).isEqualTo(firstSecret);
    }

    @Test
    void configuredJwtSecretWins() {
        DefaultUserAuthSettings settings = new DefaultUserAuthSettings(
                new InMemorySystemSettingRepository(),
                new InMemoryAccountRepository()
        );

        assertThat(settings.jwtSecret("configured-jwt-secret")).isEqualTo("configured-jwt-secret");
    }

    @Test
    void existingUsersKeepBlankPasswordPepperForCompatibility() {
        InMemoryAccountRepository accounts = new InMemoryAccountRepository();
        accounts.create(account("alice@example.com"));
        DefaultUserAuthSettings settings = new DefaultUserAuthSettings(new InMemorySystemSettingRepository(), accounts);

        assertThat(settings.passwordPepper("")).isEmpty();
    }

    private DefaultUserAccount account(String email) {
        DefaultUserAccount account = new DefaultUserAccount();
        account.setEmail(email);
        account.setDisplayName("Alice");
        account.setPasswordHash("hash");
        return account;
    }

    private static final class InMemorySystemSettingRepository implements SystemSettingRepository {

        private final Map<String, String> values = new HashMap<>();

        @Override
        public Optional<String> findByKey(String key) {
            return Optional.ofNullable(values.get(key));
        }

        @Override
        public String findOrCreate(String key, Supplier<String> valueSupplier) {
            return values.computeIfAbsent(key, ignored -> valueSupplier.get());
        }

        @Override
        public void save(String key, String value) {
            values.put(key, value);
        }

    }

    private static final class InMemoryAccountRepository implements DefaultUserAccountRepository {

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
        public boolean hasAnyAccount() {
            return !byUserId.isEmpty();
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
