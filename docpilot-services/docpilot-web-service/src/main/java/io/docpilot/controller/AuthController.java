package io.docpilot.controller;

import io.docpilot.auth.AuthAccountAction;
import io.docpilot.auth.AuthLoginFlow;
import io.docpilot.auth.AuthProvider;
import io.docpilot.auth.AuthProviderCapabilities;
import io.docpilot.common.auth.AuthContextProvider;
import io.docpilot.common.auth.AuthSubject;
import io.docpilot.common.exception.NotFoundException;
import io.docpilot.common.exception.UnauthorizedException;
import io.docpilot.common.result.Result;
import io.docpilot.common.web.auth.RequireAuth;
import io.docpilot.common.web.logging.LogMask;
import io.docpilot.infrastructure.auth.provider.AuthProviderRegistry;
import io.docpilot.infrastructure.auth.provider.AuthSession;
import io.docpilot.infrastructure.auth.provider.ChangeDisplayNameCommand;
import io.docpilot.infrastructure.auth.provider.ChangePasswordCommand;
import io.docpilot.infrastructure.auth.provider.LoginCommand;
import io.docpilot.infrastructure.auth.provider.PasswordLoginAuthProvider;
import io.docpilot.infrastructure.auth.provider.RegisterCommand;
import io.docpilot.infrastructure.auth.provider.UserProfileAuthProvider;
import io.docpilot.user.model.UserInformation;
import io.docpilot.user.provider.UserInformationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthProviderRegistry authProviderRegistry;
    private final AuthContextProvider authContextProvider;
    private final UserInformationProvider userInformationProvider;

    public AuthController(AuthProviderRegistry authProviderRegistry,
                          AuthContextProvider authContextProvider,
                          UserInformationProvider userInformationProvider) {
        this.authProviderRegistry = authProviderRegistry;
        this.authContextProvider = authContextProvider;
        this.userInformationProvider = userInformationProvider;
    }

    @PostMapping("/config")
    public Result<AuthConfigResponse> config() {
        AuthProvider provider = authProviderRegistry.currentProvider();
        log.debug("auth config requested providerId={}", provider.providerId());
        return Result.success(new AuthConfigResponse(
                provider.providerId(),
                AuthCapabilitiesResponse.from(provider.capabilities())
        ));
    }

    @PostMapping("/register")
    public Result<UserInformation> register(@RequestBody RegisterRequest request) {
        PasswordLoginAuthProvider provider = passwordLoginProvider();
        if (!authProviderRegistry.currentProvider().capabilities().hasAccountAction(AuthAccountAction.REGISTER)) {
            log.warn("auth registration rejected reason=provider-unsupported providerId={}",
                    authProviderRegistry.currentProviderId());
            throw new NotFoundException("Auth provider does not support registration");
        }
        return Result.success(provider.register(new RegisterCommand(request.email(), request.password(), request.displayName())));
    }

    @PostMapping("/login")
    public Result<AuthSession> login(@RequestBody LoginRequest request) {
        PasswordLoginAuthProvider provider = passwordLoginProvider();
        return Result.success(provider.login(new LoginCommand(request.email(), request.password())));
    }

    @PostMapping("/me")
    @RequireAuth
    public Result<UserInformation> me() {
        AuthSubject subject = currentSubject();
        UserInformation user = userInformationProvider.findByUserId(subject.getUserId())
                .orElseThrow(() -> new UnauthorizedException("Authenticated user no longer exists"));
        log.debug("auth current user resolved userId={}", user.getUserId());
        return Result.success(user);
    }

    @PostMapping("/password/change")
    @RequireAuth
    public Result<Void> changePassword(@RequestBody ChangePasswordRequest request) {
        PasswordLoginAuthProvider provider = passwordLoginProvider();
        if (!authProviderRegistry.currentProvider().capabilities().hasAccountAction(AuthAccountAction.CHANGE_PASSWORD)) {
            log.warn("auth password change rejected reason=provider-unsupported providerId={}",
                    authProviderRegistry.currentProviderId());
            throw new NotFoundException("Auth provider does not support password changes");
        }
        provider.changePassword(new ChangePasswordCommand(request.currentPassword(), request.newPassword()));
        return Result.success();
    }

    @PostMapping("/display-name/change")
    @RequireAuth
    public Result<UserInformation> changeDisplayName(@RequestBody ChangeDisplayNameRequest request) {
        AuthProvider provider = authProviderRegistry.currentProvider();
        if (!(provider instanceof UserProfileAuthProvider profileProvider)
                || !provider.capabilities().hasAccountAction(AuthAccountAction.CHANGE_DISPLAY_NAME)) {
            log.warn("auth display name change rejected reason=provider-unsupported providerId={}", provider.providerId());
            throw new NotFoundException("Auth provider does not support display name changes");
        }
        return Result.success(profileProvider.changeDisplayName(new ChangeDisplayNameCommand(request.displayName())));
    }

    private PasswordLoginAuthProvider passwordLoginProvider() {
        AuthProvider provider = authProviderRegistry.currentProvider();
        if (!(provider instanceof PasswordLoginAuthProvider passwordProvider)
                || !provider.capabilities().hasLoginFlow(AuthLoginFlow.PASSWORD_FORM)) {
            log.warn("auth password flow rejected reason=provider-unsupported providerId={}", provider.providerId());
            throw new NotFoundException("Auth provider does not support password login");
        }
        return passwordProvider;
    }

    private AuthSubject currentSubject() {
        return authContextProvider.currentSubject()
                .orElseThrow(() -> new UnauthorizedException("Authentication is required"));
    }

    public record AuthConfigResponse(String providerId, AuthCapabilitiesResponse capabilities) {
    }

    public record AuthCapabilitiesResponse(
            Set<AuthLoginFlow> loginFlows,
            Set<AuthAccountAction> accountActions,
            boolean supportsPasswordLogin,
            boolean supportsRegistration,
            boolean supportsPasswordChange,
            boolean supportsDisplayNameChange,
            boolean supportsHostToken
    ) {

        public static AuthCapabilitiesResponse from(AuthProviderCapabilities capabilities) {
            return new AuthCapabilitiesResponse(
                    capabilities.loginFlows(),
                    capabilities.accountActions(),
                    capabilities.hasLoginFlow(AuthLoginFlow.PASSWORD_FORM),
                    capabilities.hasAccountAction(AuthAccountAction.REGISTER),
                    capabilities.hasAccountAction(AuthAccountAction.CHANGE_PASSWORD),
                    capabilities.hasAccountAction(AuthAccountAction.CHANGE_DISPLAY_NAME),
                    capabilities.hasLoginFlow(AuthLoginFlow.HOST_TOKEN)
            );
        }
    }

    public record RegisterRequest(String email, @LogMask String password, String displayName) {
    }

    public record LoginRequest(String email, @LogMask String password) {
    }

    public record ChangePasswordRequest(@LogMask String currentPassword, @LogMask String newPassword) {
    }

    public record ChangeDisplayNameRequest(String displayName) {
    }

}
