package io.docpilot.infrastructure.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import io.docpilot.common.web.auth.DocPilotJwtConfig;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;

public class DefaultUserJwtIssuer {

    private final DocPilotJwtConfig config;
    private final Clock clock;

    public DefaultUserJwtIssuer(DocPilotJwtConfig config) {
        this(config, Clock.systemUTC());
    }

    DefaultUserJwtIssuer(DocPilotJwtConfig config, Clock clock) {
        this.config = config;
        this.clock = clock;
    }

    public String issue(DefaultUserAccount account) {
        long ttl = Math.max(60, config.getAccessTokenTtlSeconds());
        Instant now = clock.instant();

        JWTCreator.Builder builder = JWT.create()
                .withSubject(Long.toString(account.getUserId()))
                .withClaim("name", account.getDisplayName())
                .withClaim("displayName", account.getDisplayName())
                .withArrayClaim("roles", new String[0])
                .withIssuedAt(now)
                .withExpiresAt(now.plusSeconds(ttl));
        if (StringUtils.hasText(config.getIssuer())) {
            builder.withIssuer(config.getIssuer());
        }

        try {
            return builder.sign(Algorithm.HMAC256(config.getSecret()));
        } catch (IllegalArgumentException | JWTCreationException e) {
            throw new IllegalStateException("Failed to issue JWT", e);
        }
    }

}
