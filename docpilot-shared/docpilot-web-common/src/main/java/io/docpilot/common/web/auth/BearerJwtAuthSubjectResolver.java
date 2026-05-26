package io.docpilot.common.web.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import io.docpilot.common.auth.AuthSubject;
import io.docpilot.common.context.RequestConstants;
import io.docpilot.common.exception.UnauthorizedException;
import io.docpilot.common.json.JsonUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class BearerJwtAuthSubjectResolver implements AuthSubjectResolver {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final DocPilotJwtProperties properties;
    private final Clock clock;

    public BearerJwtAuthSubjectResolver(DocPilotJwtProperties properties) {
        this(properties, Clock.systemUTC());
    }

    BearerJwtAuthSubjectResolver(DocPilotJwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public Optional<AuthSubject> resolve(HttpServletRequest request) {
        String authorization = request.getHeader(RequestConstants.HEADER_AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(RequestConstants.BEARER_PREFIX)) {
            return Optional.empty();
        }

        String token = authorization.substring(RequestConstants.BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(token)) {
            throw new UnauthorizedException("Bearer token is empty");
        }

        Map<String, Object> claims = verifyAndReadClaims(token);
        String userId = stringClaim(claims, "sub");
        if (!StringUtils.hasText(userId)) {
            throw new UnauthorizedException("Token subject is missing");
        }

        AuthSubject subject = new AuthSubject();
        subject.setUserId(userId);
        subject.setDisplayName(firstStringClaim(claims, "displayName", "name", "preferred_username"));
        subject.setPlatformRoles(readRoles(claims));
        return Optional.of(subject);
    }

    private Map<String, Object> verifyAndReadClaims(String token) {
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            throw new UnauthorizedException("Invalid bearer token");
        }

        Map<String, Object> header = readPart(parts[0]);
        String alg = stringClaim(header, "alg");
        if (!"HS256".equals(alg)) {
            throw new UnauthorizedException("Unsupported token algorithm");
        }

        verifySignature(parts[0] + "." + parts[1], parts[2]);

        Map<String, Object> claims = readPart(parts[1]);
        validateIssuer(claims);
        validateTime(claims);
        return claims;
    }

    private Map<String, Object> readPart(String encodedPart) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(encodedPart);
            return JsonUtils.convert(new String(decoded, StandardCharsets.UTF_8), MAP_TYPE);
        } catch (RuntimeException e) {
            throw new UnauthorizedException("Invalid bearer token");
        }
    }

    private void verifySignature(String signingInput, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(properties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            String expected = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), signature.getBytes(StandardCharsets.US_ASCII))) {
                throw new UnauthorizedException("Invalid bearer token signature");
            }
        } catch (UnauthorizedException e) {
            throw e;
        } catch (Exception e) {
            throw new UnauthorizedException("Unable to verify bearer token");
        }
    }

    private void validateIssuer(Map<String, Object> claims) {
        if (!StringUtils.hasText(properties.getIssuer())) {
            return;
        }
        String issuer = stringClaim(claims, "iss");
        if (!properties.getIssuer().equals(issuer)) {
            throw new UnauthorizedException("Invalid token issuer");
        }
    }

    private void validateTime(Map<String, Object> claims) {
        long now = clock.instant().getEpochSecond();
        long skew = properties.getClockSkewSeconds();

        Long expiresAt = longClaim(claims, "exp");
        if (expiresAt != null && now - skew >= expiresAt) {
            throw new UnauthorizedException("Bearer token has expired");
        }

        Long notBefore = longClaim(claims, "nbf");
        if (notBefore != null && now + skew < notBefore) {
            throw new UnauthorizedException("Bearer token is not active yet");
        }
    }

    private Set<String> readRoles(Map<String, Object> claims) {
        Set<String> roles = new HashSet<>();
        Object rolesClaim = claims.get("roles");
        if (rolesClaim instanceof Collection<?> collection) {
            for (Object role : collection) {
                if (role != null && StringUtils.hasText(role.toString())) {
                    roles.add(role.toString());
                }
            }
        } else if (rolesClaim instanceof String text) {
            for (String role : text.split("[,\\s]+")) {
                if (StringUtils.hasText(role)) {
                    roles.add(role);
                }
            }
        }
        return roles;
    }

    private String firstStringClaim(Map<String, Object> claims, String... names) {
        for (String name : names) {
            String value = stringClaim(claims, name);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String stringClaim(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        return value == null ? null : value.toString();
    }

    private Long longClaim(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException e) {
                throw new UnauthorizedException("Invalid token numeric date: " + name);
            }
        }
        return null;
    }

}
