package io.docpilot.controller;

import io.docpilot.common.result.Result;
import io.docpilot.common.web.auth.RequireAuth;
import io.docpilot.common.web.logging.LogMask;
import io.docpilot.infrastructure.auth.DefaultUserAuthService;
import io.docpilot.infrastructure.auth.DefaultUserAuthService.AuthSession;
import io.docpilot.user.model.UserInformation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@ConditionalOnProperty(prefix = "docpilot.auth.default-user", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AuthController {

    private final DefaultUserAuthService authService;

    public AuthController(DefaultUserAuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Result<UserInformation> register(@RequestBody RegisterRequest request) {
        return Result.success(authService.register(request.email(), request.password(), request.displayName()));
    }

    @PostMapping("/login")
    public Result<AuthSession> login(@RequestBody LoginRequest request) {
        return Result.success(authService.login(request.email(), request.password()));
    }

    @PostMapping("/me")
    @RequireAuth
    public Result<UserInformation> me() {
        return Result.success(authService.currentUser());
    }

    @PostMapping("/password/change")
    @RequireAuth
    public Result<Void> changePassword(@RequestBody ChangePasswordRequest request) {
        authService.changePassword(request.currentPassword(), request.newPassword());
        return Result.success();
    }

    @PostMapping("/display-name/change")
    @RequireAuth
    public Result<UserInformation> changeDisplayName(@RequestBody ChangeDisplayNameRequest request) {
        return Result.success(authService.changeDisplayName(request.displayName()));
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
