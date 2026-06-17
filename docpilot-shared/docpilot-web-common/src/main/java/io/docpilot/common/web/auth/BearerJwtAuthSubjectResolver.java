package io.docpilot.common.web.auth;

import io.docpilot.common.auth.AuthSubject;
import io.docpilot.common.context.RequestConstants;
import io.docpilot.common.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

import java.util.Optional;

public class BearerJwtAuthSubjectResolver implements AuthSubjectResolver {

    private final JwtTokenVerifier jwtTokenVerifier;

    public BearerJwtAuthSubjectResolver(DocPilotJwtConfig config) {
        this(new JwtTokenVerifier(config));
    }

    public BearerJwtAuthSubjectResolver(JwtTokenVerifier jwtTokenVerifier) {
        this.jwtTokenVerifier = jwtTokenVerifier;
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

        return jwtTokenVerifier.verify(token);
    }

}
