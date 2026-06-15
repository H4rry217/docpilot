package io.docpilot.controller;

import io.docpilot.common.result.Result;
import io.docpilot.common.web.auth.RequireAuth;
import io.docpilot.workspace.inlinecompletion.InlineCompletionService;
import io.docpilot.workspace.model.request.InlineCompletionRequest;
import io.docpilot.workspace.model.response.InlineCompletionCompleteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Non-streaming inline completion transport.
 */
@RestController
@RequestMapping("/inline-completion")
@RequireAuth
public class InlineCompletionController {

    private static final Logger log = LoggerFactory.getLogger(InlineCompletionController.class);

    private final InlineCompletionService inlineCompletionService;

    public InlineCompletionController(InlineCompletionService inlineCompletionService) {
        this.inlineCompletionService = inlineCompletionService;
    }

    /**
     * Completes the current cursor anchor and returns ranked insertion candidates.
     */
    @PostMapping("/complete")
    public Result<InlineCompletionCompleteResponse> complete(@RequestBody InlineCompletionRequest request) {
        try {
            return Result.success(inlineCompletionService.complete(request));
        } catch (IllegalArgumentException exception) {
            log.warn("inline completion bad request workspaceId={} documentId={} trigger={} cursor={} currentBlockType={} candidateCount={} message={}",
                    request == null ? null : request.workspaceId(),
                    request == null ? null : request.documentId(),
                    request == null ? null : request.trigger(),
                    cursorSummary(request),
                    currentBlockType(request),
                    request == null ? null : request.candidateCount(),
                    exception.getMessage());
            throw exception;
        }
    }

    private String cursorSummary(InlineCompletionRequest request) {
        if (request == null || request.cursor() == null) {
            return null;
        }
        return request.cursor().from() + "-" + request.cursor().to();
    }

    private String currentBlockType(InlineCompletionRequest request) {
        if (request == null || request.currentBlock() == null) {
            return null;
        }
        return request.currentBlock().type();
    }

}
