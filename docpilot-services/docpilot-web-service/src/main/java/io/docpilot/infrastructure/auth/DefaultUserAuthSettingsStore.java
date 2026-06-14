package io.docpilot.infrastructure.auth;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * JDBC-backed store for authentication runtime settings.
 */
public class DefaultUserAuthSettingsStore {

    /**
     * Setting key used to persist the generated password pepper.
     */
    public static final String PASSWORD_PEPPER_KEY = "auth.password-pepper";

    /**
     * Setting key used to persist the generated JWT signing secret.
     */
    public static final String JWT_SECRET_KEY = "auth.jwt-secret";

    /**
     * Secure random source used for generated authentication secrets.
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Schema DDL for the settings table used by local authentication.
     */
    private static final String CREATE_SETTINGS_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS docpilot_setting (
                setting_key VARCHAR(120) NOT NULL,
                setting_value VARCHAR(2048) NOT NULL,
                create_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                update_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                PRIMARY KEY (setting_key)
            )
            """;

    /**
     * JDBC access object for settings reads and writes.
     */
    private final JdbcTemplate jdbcTemplate;

    /**
     * Whether this store is allowed to create the settings table on demand.
     */
    private final boolean initializeSchema;

    /**
     * In-process guard that prevents repeated schema initialization attempts.
     */
    private volatile boolean schemaInitialized;

    /**
     * Creates an authentication settings store.
     *
     * @param dataSource settings database connection source.
     * @param initializeSchema whether the settings table should be created on demand.
     */
    public DefaultUserAuthSettingsStore(DataSource dataSource, boolean initializeSchema) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.initializeSchema = initializeSchema;
    }

    /**
     * Returns the configured pepper or creates one for new installations.
     *
     * @param configuredPepper pepper supplied by configuration.
     * @return effective password pepper.
     */
    public String passwordPepper(String configuredPepper) {
        if (StringUtils.hasText(configuredPepper)) {
            return configuredPepper;
        }
        return findOrCreateSetting(PASSWORD_PEPPER_KEY, this::initialPasswordPepper);
    }

    /**
     * Returns the configured JWT secret or creates a persistent one for local auth.
     *
     * @param configuredSecret secret supplied by configuration.
     * @return effective JWT signing secret.
     */
    public String jwtSecret(String configuredSecret) {
        if (StringUtils.hasText(configuredSecret)) {
            return configuredSecret;
        }
        return findOrCreateSetting(JWT_SECRET_KEY, this::generateSecret);
    }

    private String findOrCreateSetting(String key, Supplier<String> valueSupplier) {
        initializeSchemaIfNeeded();
        Optional<String> existing = findSetting(key);
        if (existing.isPresent()) {
            return existing.get();
        }

        String value = valueSupplier.get();
        Instant now = Instant.now();
        try {
            jdbcTemplate.update("""
                    INSERT INTO docpilot_setting (setting_key, setting_value, create_time, update_time)
                    VALUES (?, ?, ?, ?)
                    """, key, value, Timestamp.from(now), Timestamp.from(now));
            return value;
        } catch (DuplicateKeyException e) {
            // A concurrent process may have created the same setting after our read; use its value.
            return findSetting(key)
                    .orElseThrow(() -> new IllegalStateException("Failed to read initialized setting: " + key, e));
        }
    }

    private Optional<String> findSetting(String key) {
        List<String> values = jdbcTemplate.query(
                "SELECT setting_value FROM docpilot_setting WHERE setting_key = ?",
                (resultSet, rowNumber) -> resultSet.getString("setting_value"),
                key
        );
        return values.stream().findFirst();
    }

    private void initializeSchemaIfNeeded() {
        if (!initializeSchema || schemaInitialized) {
            return;
        }
        synchronized (this) {
            if (!schemaInitialized) {
                jdbcTemplate.execute(CREATE_SETTINGS_TABLE_SQL);
                schemaInitialized = true;
            }
        }
    }

    private String initialPasswordPepper() {
        return hasExistingUsers() ? "" : generateSecret();
    }

    private boolean hasExistingUsers() {
        try {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM docpilot_user", Integer.class);
            return count != null && count > 0;
        } catch (DataAccessException e) {
            return false;
        }
    }

    private String generateSecret() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

}
