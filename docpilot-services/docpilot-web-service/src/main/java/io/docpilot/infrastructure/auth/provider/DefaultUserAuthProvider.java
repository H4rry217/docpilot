package io.docpilot.infrastructure.auth.provider;

import io.docpilot.auth.AuthAccountAction;
import io.docpilot.auth.AuthLoginFlow;
import io.docpilot.auth.AuthPrincipal;
import io.docpilot.auth.AuthProvider;
import io.docpilot.auth.AuthProviderCapabilities;
import io.docpilot.auth.AuthRequest;
import io.docpilot.common.auth.AuthSubject;
import io.docpilot.common.exception.UnauthorizedException;
import io.docpilot.common.web.auth.JwtTokenVerifier;
import io.docpilot.infrastructure.auth.DefaultUserAuthConfig;
import io.docpilot.infrastructure.auth.DefaultUserAuthService;
import io.docpilot.user.model.UserInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Built-in provider for DocPilot's default email/password user auth stack.
 */
public class DefaultUserAuthProvider implements AuthProvider, PasswordLoginAuthProvider, UserProfileAuthProvider {

    public static final String PROVIDER_ID = "local-password";

    private static final Logger log = LoggerFactory.getLogger(DefaultUserAuthProvider.class);

    private final DefaultUserAuthService authService;
    private final DefaultUserAuthConfig config;
    private final JwtTokenVerifier jwtTokenVerifier;

    public DefaultUserAuthProvider(DefaultUserAuthService authService,
                                   DefaultUserAuthConfig config,
                                   JwtTokenVerifier jwtTokenVerifier) {
        this.authService = authService;
        this.config = config;
        this.jwtTokenVerifier = jwtTokenVerifier;
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public AuthProviderCapabilities capabilities() {
        Set<AuthAccountAction> accountActions = new LinkedHashSet<>();
        if (config.isAllowRegistration()) {
            accountActions.add(AuthAccountAction.REGISTER);
        }
        accountActions.add(AuthAccountAction.CHANGE_PASSWORD);
        accountActions.add(AuthAccountAction.CHANGE_DISPLAY_NAME);
        return new AuthProviderCapabilities(Set.of(AuthLoginFlow.PASSWORD_FORM), accountActions);
    }

    @Override
    public Optional<Long> resolveUserId(AuthPrincipal principal) {
        if (!StringUtils.hasText(principal.subject())) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(principal.subject()));
        } catch (NumberFormatException e) {
            throw new UnauthorizedException("Authentication subject is invalid");
        }
    }

    @Override
    public Optional<AuthPrincipal> authenticate(AuthRequest request) {
        Optional<String> bearerToken = request.bearerToken();
        if (bearerToken.isEmpty()) {
            log.debug("default user auth skipped reason=bearer-token-missing method={} path={}",
                    request.method(), request.path());
            return Optional.empty();
        }
        Optional<AuthPrincipal> principal = bearerToken
                .flatMap(jwtTokenVerifier::verify)
                .map(this::toPrincipal);
        if (principal.isPresent()) {
            log.debug("default user auth accepted userId={} method={} path={}",
                    principal.get().subject(), request.method(), request.path());
        } else {
            log.warn("default user auth rejected reason=invalid-bearer-token method={} path={}",
                    request.method(), request.path());
        }
        return principal;
    }

    @Override
    public AuthSession login(LoginCommand command) {
        return authService.login(command.email(), command.password());
    }

    @Override
    public UserInformation register(RegisterCommand command) {
        return authService.register(command.email(), command.password(), command.displayName());
    }

    @Override
    public void changePassword(ChangePasswordCommand command) {
        authService.changePassword(command.currentPassword(), command.newPassword());
    }

    @Override
    public UserInformation changeDisplayName(ChangeDisplayNameCommand command) {
        return authService.changeDisplayName(command.displayName());
    }

    private AuthPrincipal toPrincipal(AuthSubject subject) {
        return AuthPrincipal.of(PROVIDER_ID, String.valueOf(subject.getUserId()), null, subject.getDisplayName(), subject.getPlatformRoles());
    }

}
