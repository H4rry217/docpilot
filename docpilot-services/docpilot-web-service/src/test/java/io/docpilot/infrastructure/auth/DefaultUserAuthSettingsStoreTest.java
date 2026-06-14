package io.docpilot.infrastructure.auth;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultUserAuthSettingsStoreTest {

    @Test
    void createsAndReusesGeneratedPasswordPepper() {
        DriverManagerDataSource dataSource = dataSource();
        DefaultUserAuthSettingsStore firstStore = new DefaultUserAuthSettingsStore(dataSource, true);

        String firstPepper = firstStore.passwordPepper("");
        String sameStorePepper = firstStore.passwordPepper(null);
        String secondStorePepper = new DefaultUserAuthSettingsStore(dataSource, true).passwordPepper("");

        assertThat(firstPepper).isNotBlank();
        assertThat(sameStorePepper).isEqualTo(firstPepper);
        assertThat(secondStorePepper).isEqualTo(firstPepper);
    }

    @Test
    void configuredPasswordPepperWins() {
        DefaultUserAuthSettingsStore store = new DefaultUserAuthSettingsStore(dataSource(), true);

        assertThat(store.passwordPepper("configured-pepper")).isEqualTo("configured-pepper");
    }

    @Test
    void createsAndReusesGeneratedJwtSecret() {
        DriverManagerDataSource dataSource = dataSource();
        DefaultUserAuthSettingsStore firstStore = new DefaultUserAuthSettingsStore(dataSource, true);

        String firstSecret = firstStore.jwtSecret("");
        String sameStoreSecret = firstStore.jwtSecret(null);
        String secondStoreSecret = new DefaultUserAuthSettingsStore(dataSource, true).jwtSecret("");

        assertThat(firstSecret).isNotBlank();
        assertThat(sameStoreSecret).isEqualTo(firstSecret);
        assertThat(secondStoreSecret).isEqualTo(firstSecret);
    }

    @Test
    void configuredJwtSecretWins() {
        DefaultUserAuthSettingsStore store = new DefaultUserAuthSettingsStore(dataSource(), true);

        assertThat(store.jwtSecret("configured-jwt-secret")).isEqualTo("configured-jwt-secret");
    }

    @Test
    void existingUsersKeepBlankPasswordPepperForCompatibility() {
        DriverManagerDataSource dataSource = dataSource();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE docpilot_user (
                    id BIGINT NOT NULL AUTO_INCREMENT,
                    email VARCHAR(255) NOT NULL,
                    display_name VARCHAR(80) NOT NULL,
                    password_hash VARCHAR(255) NOT NULL,
                    create_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    update_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
                    PRIMARY KEY (id)
                )
                """);
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO docpilot_user (email, display_name, password_hash, create_time, update_time)
                VALUES (?, ?, ?, ?, ?)
                """, "alice@example.com", "Alice", "hash", Timestamp.from(now), Timestamp.from(now));

        DefaultUserAuthSettingsStore store = new DefaultUserAuthSettingsStore(dataSource, true);

        assertThat(store.passwordPepper("")).isEmpty();
    }

    private DriverManagerDataSource dataSource() {
        String databaseName = UUID.randomUUID().toString().replace("-", "");
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl("jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }

}
