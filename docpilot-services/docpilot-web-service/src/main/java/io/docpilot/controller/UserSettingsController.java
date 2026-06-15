package io.docpilot.controller;

import io.docpilot.common.auth.AuthContextProvider;
import io.docpilot.common.auth.AuthSubject;
import io.docpilot.common.exception.UnauthorizedException;
import io.docpilot.common.result.Result;
import io.docpilot.common.web.auth.RequireAuth;
import io.docpilot.user.application.UserSettingManager;
import io.docpilot.user.model.request.UserSettingsGetRequest;
import io.docpilot.user.model.request.UserSettingsRemoveRequest;
import io.docpilot.user.model.request.UserSettingsSaveRequest;
import io.docpilot.user.model.response.UserSettingsResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP API for current-user cloud settings.
 */
@RestController
@RequestMapping("/user/settings")
@RequireAuth
public class UserSettingsController {

    /**
     * User setting application service.
     */
    private final UserSettingManager userSettingManager;

    /**
     * Current request auth subject provider.
     */
    private final AuthContextProvider authContextProvider;

    public UserSettingsController(UserSettingManager userSettingManager,
                                  AuthContextProvider authContextProvider) {
        this.userSettingManager = userSettingManager;
        this.authContextProvider = authContextProvider;
    }

    @PostMapping("/get")
    public Result<UserSettingsResponse> get(@RequestBody(required = false) UserSettingsGetRequest request) {
        return Result.success(userSettingManager.getSettings(currentUserId(), request == null ? null : request.keys()));
    }

    @PostMapping("/save")
    public Result<UserSettingsResponse> save(@RequestBody(required = false) UserSettingsSaveRequest request) {
        return Result.success(userSettingManager.saveSettings(currentUserId(), request == null ? null : request.values()));
    }

    @PostMapping("/remove")
    public Result<UserSettingsResponse> remove(@RequestBody(required = false) UserSettingsRemoveRequest request) {
        return Result.success(userSettingManager.removeSettings(currentUserId(), request == null ? null : request.keys()));
    }

    private Long currentUserId() {
        AuthSubject subject = authContextProvider.currentSubject()
                .orElseThrow(() -> new UnauthorizedException("Authentication is required"));
        return subject.getUserId();
    }

}
