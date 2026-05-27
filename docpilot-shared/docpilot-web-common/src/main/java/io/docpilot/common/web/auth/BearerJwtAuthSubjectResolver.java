package io.docpilot.common.web.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.Verification;
import io.docpilot.common.auth.AuthSubject;
import io.docpilot.common.context.RequestConstants;
import io.docpilot.common.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class BearerJwtAuthSubjectResolver implements AuthSubjectResolver {

    private final DocPilotJwtConfig config;

    public BearerJwtAuthSubjectResolver(DocPilotJwtConfig config) {
        this.config = config;
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

        DecodedJWT jwt = verifyAndReadJwt(token);
        Long userId = subjectId(jwt);
        if (userId == null) {
            throw new UnauthorizedException("Token subject is missing");
        }

        AuthSubject subject = new AuthSubject();
        subject.setUserId(userId);
        subject.setDisplayName(firstStringClaim(jwt, "displayName", "name", "preferred_username"));
        subject.setPlatformRoles(readRoles(jwt));
        return Optional.of(subject);
    }

    private DecodedJWT verifyAndReadJwt(String token) {
        try {
            Verification verification = JWT.require(Algorithm.HMAC256(config.getSecret()))
                    .acceptLeeway(config.getClockSkewSeconds());
            if (StringUtils.hasText(config.getIssuer())) {
                verification.withIssuer(config.getIssuer());
            }
            return verification.build().verify(token);
        } catch (TokenExpiredException e) {
            throw new UnauthorizedException("Bearer token has expired");
        } catch (JWTVerificationException | IllegalArgumentException e) {
            throw new UnauthorizedException("Invalid bearer token");
        }
    }

    private String firstStringClaim(DecodedJWT jwt, String... names) {
        for (String name : names) {
            String value = stringClaim(jwt, name);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String stringClaim(DecodedJWT jwt, String name) {
        try {
            Claim claim = jwt.getClaim(name);
            return claim == null || claim.isMissing() || claim.isNull() ? null : claim.asString();
        } catch (JWTDecodeException e) {
            throw new UnauthorizedException("Invalid token claim: " + name);
        }
    }

    private Long subjectId(DecodedJWT jwt) {
        String subject = jwt.getSubject();
        if (StringUtils.hasText(subject)) {
            return parseLongClaim("sub", subject);
        }

        try {
            Claim subjectClaim = jwt.getClaim("sub");
            if (subjectClaim == null || subjectClaim.isMissing() || subjectClaim.isNull()) {
                return null;
            }
            Long numericSubject = subjectClaim.asLong();
            if (numericSubject != null) {
                return numericSubject;
            }
            String textSubject = subjectClaim.asString();
            return StringUtils.hasText(textSubject) ? parseLongClaim("sub", textSubject) : null;
        } catch (JWTDecodeException e) {
            throw new UnauthorizedException("Invalid token claim: sub");
        }
    }

    private Long parseLongClaim(String name, String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new UnauthorizedException("Invalid token numeric claim: " + name);
        }
    }

    private Set<String> readRoles(DecodedJWT jwt) {
        Set<String> roles = new HashSet<>();
        Claim rolesClaim = jwt.getClaim("roles");
        if (rolesClaim == null || rolesClaim.isMissing() || rolesClaim.isNull()) {
            return roles;
        }

        try {
            Collection<String> roleList = rolesClaim.asList(String.class);
            if (roleList != null) {
                roleList.stream()
                        .filter(StringUtils::hasText)
                        .forEach(roles::add);
                return roles;
            }

            String text = rolesClaim.asString();
            if (StringUtils.hasText(text)) {
                for (String role : text.split("[,\\s]+")) {
                    if (StringUtils.hasText(role)) {
                        roles.add(role);
                    }
                }
            }
            return roles;
        } catch (JWTDecodeException e) {
            throw new UnauthorizedException("Invalid token claim: roles");
        }
    }

}
