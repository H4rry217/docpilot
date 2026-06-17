package io.docpilot.infrastructure.auth;

import io.docpilot.system.repository.SystemSettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Default auth runtime settings backed by system settings.
 */
public class DefaultUserAuthSettings {

    private static final Logger log = LoggerFactory.getLogger(DefaultUserAuthSettings.class);

    /**
     * Setting key used to persist the generated password pepper.
     */
    public static final String PASSWORD_PEPPER_KEY = "auth.password-pepper";

    /**
     * Setting key used to persist the generated JWT signing secret.
     */
    public static final String JWT_SECRET_KEY = "auth.jwt-secret";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SystemSettingRepository systemSettingRepository;
    private final DefaultUserAccountRepository accountRepository;

    public DefaultUserAuthSettings(SystemSettingRepository systemSettingRepository,
                                   DefaultUserAccountRepository accountRepository) {
        this.systemSettingRepository = systemSettingRepository;
        this.accountRepository = accountRepository;
    }

    public String passwordPepper(String configuredPepper) {
        if (StringUtils.hasText(configuredPepper)) {
            log.info("default user auth password pepper resolved source=config");
            return configuredPepper;
        }
        log.info("default user auth password pepper resolved source=system-setting key={}", PASSWORD_PEPPER_KEY);
        return systemSettingRepository.findOrCreate(PASSWORD_PEPPER_KEY, this::initialPasswordPepper);
    }

    public String jwtSecret(String configuredSecret) {
        if (StringUtils.hasText(configuredSecret)) {
            log.info("default user auth jwt secret resolved source=config");
            return configuredSecret;
        }
        log.info("default user auth jwt secret resolved source=system-setting key={}", JWT_SECRET_KEY);
        return systemSettingRepository.findOrCreate(JWT_SECRET_KEY, this::generateSecret);
    }

    private String initialPasswordPepper() {
        if (accountRepository.hasAnyAccount()) {
            log.info("default user auth password pepper initialized empty reason=existing-default-users key={}", PASSWORD_PEPPER_KEY);
            return "";
        }
        log.info("default user auth password pepper initialized generated key={}", PASSWORD_PEPPER_KEY);
        return generateSecret();
    }

    private String generateSecret() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

}
