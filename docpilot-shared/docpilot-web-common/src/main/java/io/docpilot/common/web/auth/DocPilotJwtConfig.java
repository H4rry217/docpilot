package io.docpilot.common.web.auth;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.Base64;

@Getter
@Setter
@ConfigurationProperties(prefix = "docpilot.auth.jwt")
public class DocPilotJwtConfig {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * HMAC secret for the default HS256 bearer-token resolver.
     * A process-local random secret is generated when this is left blank.
     */
    private String secret;

    /**
     * Optional issuer claim to enforce.
     */
    private String issuer;

    /**
     * Allowed clock skew in seconds for exp/nbf checks.
     */
    private long clockSkewSeconds = 60;

    /**
     * Default access-token lifetime in seconds for local token issuers.
     */
    private long accessTokenTtlSeconds = 604800;

    public synchronized String getSecret() {
        if (!StringUtils.hasText(secret)) {
            byte[] bytes = new byte[32];
            SECURE_RANDOM.nextBytes(bytes);
            secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        }
        return secret;
    }

    public synchronized String getConfiguredSecret() {
        return secret;
    }

}
