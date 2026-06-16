package io.docpilot.controller;

import io.docpilot.common.auth.AuthContextProvider;
import io.docpilot.common.auth.AuthSubject;
import io.docpilot.common.exception.NotFoundException;
import io.docpilot.common.exception.UnauthorizedException;
import io.docpilot.common.result.Result;
import io.docpilot.common.web.auth.RequireAuth;
import io.docpilot.common.web.logging.LogMask;
import io.docpilot.infrastructure.auth.provider.AuthProvider;
import io.docpilot.infrastructure.auth.provider.AuthProviderCapabilities;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

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
        return Result.success(new AuthConfigResponse(provider.providerId(), provider.capabilities()));
    }

    @PostMapping("/register")
    public Result<UserInformation> register(@RequestBody RegisterRequest request) {
        PasswordLoginAuthProvider provider = passwordLoginProvider();
        if (!authProviderRegistry.currentProvider().capabilities().supportsRegistration()) {
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
        return Result.success(user);
    }

    @PostMapping("/password/change")
    @RequireAuth
    public Result<Void> changePassword(@RequestBody ChangePasswordRequest request) {
        PasswordLoginAuthProvider provider = passwordLoginProvider();
        if (!authProviderRegistry.currentProvider().capabilities().supportsPasswordChange()) {
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
                || !provider.capabilities().supportsDisplayNameChange()) {
            throw new NotFoundException("Auth provider does not support display name changes");
        }
        return Result.success(profileProvider.changeDisplayName(new ChangeDisplayNameCommand(request.displayName())));
    }

    private PasswordLoginAuthProvider passwordLoginProvider() {
        AuthProvider provider = authProviderRegistry.currentProvider();
        if (!(provider instanceof PasswordLoginAuthProvider passwordProvider)
                || !provider.capabilities().supportsPasswordLogin()) {
            throw new NotFoundException("Auth provider does not support password login");
        }
        return passwordProvider;
    }

    private AuthSubject currentSubject() {
        return authContextProvider.currentSubject()
                .orElseThrow(() -> new UnauthorizedException("Authentication is required"));
    }

    public record AuthConfigResponse(String providerId, AuthProviderCapabilities capabilities) {
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
