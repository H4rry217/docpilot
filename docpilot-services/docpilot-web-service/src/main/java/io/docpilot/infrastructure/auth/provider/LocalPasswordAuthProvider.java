package io.docpilot.infrastructure.auth.provider;

import io.docpilot.common.auth.AuthSubject;
import io.docpilot.common.exception.UnauthorizedException;
import io.docpilot.common.web.auth.BearerJwtAuthSubjectResolver;
import io.docpilot.common.web.auth.DocPilotJwtConfig;
import io.docpilot.infrastructure.auth.DefaultUserAuthConfig;
import io.docpilot.infrastructure.auth.DefaultUserAuthService;
import io.docpilot.user.model.UserInformation;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * Built-in provider that wraps the legacy DocPilot email/password auth stack.
 */
public class LocalPasswordAuthProvider implements AuthProvider, PasswordLoginAuthProvider, UserProfileAuthProvider {

    public static final String PROVIDER_ID = "local-password";

    private final DefaultUserAuthService authService;
    private final DefaultUserAuthConfig config;
    private final BearerJwtAuthSubjectResolver jwtResolver;

    public LocalPasswordAuthProvider(DefaultUserAuthService authService,
                                     DefaultUserAuthConfig config,
                                     DocPilotJwtConfig jwtConfig) {
        this.authService = authService;
        this.config = config;
        this.jwtResolver = new BearerJwtAuthSubjectResolver(jwtConfig);
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public AuthProviderCapabilities capabilities() {
        return AuthProviderCapabilities.localPassword(config.isAllowRegistration());
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
    public Optional<AuthPrincipal> authenticateRequest(AuthRequest request) {
        return jwtResolver.resolve(request.servletRequest())
                .map(this::toPrincipal);
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
