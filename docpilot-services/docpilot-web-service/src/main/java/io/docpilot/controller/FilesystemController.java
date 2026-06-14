package io.docpilot.controller;

import io.docpilot.common.result.Result;
import io.docpilot.common.web.auth.RequireAuth;
import io.docpilot.workspace.filesystem.UserFilesystemFailureMode;
import io.docpilot.workspace.filesystem.UserFilesystemService;
import io.docpilot.workspace.model.request.UserFilesystemRetrieveCommand;
import io.docpilot.workspace.model.request.UserFilesystemRetrieveRequest;
import io.docpilot.workspace.model.response.UserFilesystemRetrieveResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for user-scoped virtual filesystem operations.
 */
@RestController
@RequestMapping("/filesystem")
@RequireAuth
public class FilesystemController {

    /**
     * User filesystem application facade.
     */
    @Autowired
    private UserFilesystemService userFilesystemService;

    /**
     * Retrieves ranked context from the current user's filesystem.
     */
    @PostMapping("/retrieve")
    public Result<UserFilesystemRetrieveResponse> retrieve(@RequestBody UserFilesystemRetrieveRequest request) {
        UserFilesystemRetrieveRequest effectiveRequest = request == null
                ? new UserFilesystemRetrieveRequest(null, null, null, null, null)
                : request;
        UserFilesystemRetrieveCommand command = new UserFilesystemRetrieveCommand();
        command.setPath(effectiveRequest.path());
        command.setQuery(effectiveRequest.query());
        command.setTopK(effectiveRequest.topK());
        command.setMaxCharsPerHit(effectiveRequest.maxCharsPerHit());
        command.setFailureMode(UserFilesystemFailureMode.parse(effectiveRequest.failureMode()));
        return Result.success(userFilesystemService.retrieve(command));
    }

}
