package io.docpilot.controller;

import io.docpilot.ai.model.ChatStreamEvent;
import io.docpilot.ai.model.ChatStreamEventType;
import io.docpilot.common.exception.DocPilotException;
import io.docpilot.common.result.Result;
import io.docpilot.common.result.StatusCode;
import io.docpilot.common.web.auth.RequireAuth;
import io.docpilot.workspace.inlinecompletion.InlineCompletionService;
import io.docpilot.workspace.inlinecompletion.InlineCompletionStream;
import io.docpilot.workspace.model.request.InlineCompletionRequest;
import io.docpilot.workspace.model.response.InlineCompletionDeltaResponse;
import io.docpilot.workspace.model.response.InlineCompletionDoneResponse;
import io.docpilot.workspace.model.response.InlineCompletionErrorResponse;
import io.docpilot.workspace.model.response.InlineCompletionMetaResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Streaming inline completion transport.
 */
@RestController
@RequestMapping("/inline-completion")
@RequireAuth
public class InlineCompletionController {

    private static final Logger log = LoggerFactory.getLogger(InlineCompletionController.class);

    private static final long SSE_TIMEOUT_MS = 120_000L;

    private static final int LOG_PREVIEW_MAX_CHARS = 160;

    private final InlineCompletionService inlineCompletionService;

    public InlineCompletionController(InlineCompletionService inlineCompletionService) {
        this.inlineCompletionService = inlineCompletionService;
    }

    @PostMapping(value = "/stream", produces = {
            MediaType.TEXT_EVENT_STREAM_VALUE,
            MediaType.APPLICATION_JSON_VALUE
    })
    public ResponseEntity<?> stream(@RequestBody InlineCompletionRequest request) {
        InlineCompletionStream stream;
        try {
            stream = inlineCompletionService.stream(request);
        } catch (DocPilotException exception) {
            StatusCode statusCode = exception.getStatusCode();
            return ResponseEntity
                    .status(HttpStatusCode.valueOf(statusCode.httpStatus()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Result.failure(statusCode, exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity
                    .status(HttpStatusCode.valueOf(StatusCode.BAD_REQUEST.httpStatus()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Result.failure(StatusCode.BAD_REQUEST, exception.getMessage()));
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(emitter(stream));
    }

    private SseEmitter emitter(InlineCompletionStream stream) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        long startedAt = System.currentTimeMillis();
        StringBuilder markdown = new StringBuilder();
        AtomicInteger deltaChars = new AtomicInteger();
        AtomicBoolean finished = new AtomicBoolean(false);
        AtomicReference<Disposable> subscriptionRef = new AtomicReference<>();
        log.debug("inline completion stream start completionId={} modelId={} shape={}",
                stream.completionId(), stream.modelId(), stream.shape());

        Runnable cancel = () -> {
            Disposable subscription = subscriptionRef.getAndSet(null);
            if (subscription != null && !subscription.isDisposed()) {
                subscription.dispose();
            }
            if (finished.compareAndSet(false, true)) {
                log.info("inline completion cancelled completionId={} durationMs={} deltaChars={}",
                        stream.completionId(), System.currentTimeMillis() - startedAt, deltaChars.get());
            }
        };
        emitter.onCompletion(cancel);
        emitter.onTimeout(() -> {
            cancel.run();
            completeQuietly(emitter);
        });
        emitter.onError(error -> cancel.run());

        Disposable startTask = Schedulers.boundedElastic().schedule(() -> startSending(
                emitter,
                stream,
                startedAt,
                markdown,
                deltaChars,
                finished,
                subscriptionRef
        ), 10, TimeUnit.MILLISECONDS);
        subscriptionRef.set(startTask);
        return emitter;
    }

    private void startSending(SseEmitter emitter,
                              InlineCompletionStream stream,
                              long startedAt,
                              StringBuilder markdown,
                              AtomicInteger deltaChars,
                              AtomicBoolean finished,
                              AtomicReference<Disposable> subscriptionRef) {
        if (finished.get()) {
            return;
        }
        if (!send(emitter, stream, "meta", new InlineCompletionMetaResponse(
                stream.completionId(),
                stream.modelId(),
                stream.shape()
        ))) {
            return;
        }

        if (finished.get()) {
            return;
        }
        Disposable subscription = stream.events().subscribe(
                event -> handleEvent(emitter, stream, event, markdown, deltaChars, subscriptionRef),
                error -> {
                    if (finished.compareAndSet(false, true)) {
                        send(emitter, stream, "error", new InlineCompletionErrorResponse(
                                "AI_STREAM_ERROR",
                                error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()
                        ));
                        log.warn("inline completion stream failed completionId={} durationMs={} deltaChars={}",
                                stream.completionId(), System.currentTimeMillis() - startedAt, deltaChars.get(), error);
                        completeQuietly(emitter);
                    }
                },
                () -> {
                    if (finished.compareAndSet(false, true)) {
                        String finalMarkdown = markdown.toString();
                        if (finalMarkdown.isBlank()) {
                            log.warn("inline completion model output empty completionId={} durationMs={} diagnostics={}",
                                    stream.completionId(), System.currentTimeMillis() - startedAt,
                                    stream.diagnostics().size());
                        } else {
                            log.debug("inline completion model output full completionId={} markdown={}",
                                    stream.completionId(), finalMarkdown);
                        }
                        send(emitter, stream, "done", new InlineCompletionDoneResponse(
                                finalMarkdown,
                                InlineCompletionService.previewText(finalMarkdown, stream.shape()),
                                stream.shape(),
                                stream.diagnostics()
                        ));
                        log.info("inline completion stream done completionId={} durationMs={} chars={} diagnostics={} preview={}",
                                stream.completionId(), System.currentTimeMillis() - startedAt,
                                deltaChars.get(), stream.diagnostics().size(), preview(finalMarkdown));
                        completeQuietly(emitter);
                    }
                }
        );
        Disposable previous = subscriptionRef.getAndSet(subscription);
        if (previous != null && previous != subscription && !previous.isDisposed()) {
            previous.dispose();
        }
        if (finished.get()) {
            Disposable active = subscriptionRef.getAndSet(null);
            if (active != null && !active.isDisposed()) {
                active.dispose();
            }
        }
    }

    private void handleEvent(SseEmitter emitter,
                             InlineCompletionStream stream,
                             ChatStreamEvent event,
                             StringBuilder markdown,
                             AtomicInteger deltaChars,
                             AtomicReference<Disposable> subscriptionRef) {
        if (event == null) {
            log.debug("inline completion model event skipped completionId={} reason=null-event",
                    stream.completionId());
            return;
        }
        if (event.getType() != ChatStreamEventType.MESSAGE_DELTA || event.getDelta() == null) {
            log.debug("inline completion model event skipped completionId={} type={} finishReason={} deltaPresent={}",
                    stream.completionId(), event.getType(), event.getFinishReason(), event.getDelta() != null);
            return;
        }
        Object content = event.getDelta().getContent();
        String delta = deltaText(content);
        log.debug("inline completion model raw delta completionId={} contentType={} rawPreview={}",
                stream.completionId(), contentType(content), preview(content));
        if (delta.isEmpty()) {
            log.warn("inline completion model delta empty completionId={} contentType={} rawPreview={}",
                    stream.completionId(), contentType(content), preview(content));
            return;
        }
        markdown.append(delta);
        int totalChars = deltaChars.addAndGet(delta.length());
        log.debug("inline completion model delta completionId={} deltaChars={} totalChars={} preview={}",
                stream.completionId(), delta.length(), totalChars, preview(delta));
        if (!send(emitter, stream, "delta", new InlineCompletionDeltaResponse(delta))) {
            Disposable subscription = subscriptionRef.getAndSet(null);
            if (subscription != null) {
                subscription.dispose();
            }
        }
    }

    private boolean send(SseEmitter emitter, InlineCompletionStream stream, String name, Object data) {
        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event().name(name).data(data));
            }
            return true;
        } catch (IOException | RuntimeException exception) {
            log.info("inline completion client disconnected completionId={} event={} exception={} message={}",
                    stream.completionId(), name, exception.getClass().getSimpleName(), exception.getMessage());
            completeQuietly(emitter);
            return false;
        }
    }

    private void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (RuntimeException ignored) {
            // The servlet response may already be marked unusable after a client disconnect.
        }
    }

    private String deltaText(Object content) {
        if (content == null) {
            return "";
        }
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof Collection<?> values) {
            StringBuilder builder = new StringBuilder();
            for (Object value : values) {
                builder.append(deltaText(value));
            }
            return builder.toString();
        }
        if (content instanceof Map<?, ?> map) {
            Object text = map.get("text");
            if (text == null) {
                text = map.get("content");
            }
            return deltaText(text);
        }
        return "";
    }

    private String contentType(Object content) {
        return content == null ? "null" : content.getClass().getName();
    }

    private String preview(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value)
                .replace("\r", "\\r")
                .replace("\n", "\\n");
        if (text.length() <= LOG_PREVIEW_MAX_CHARS) {
            return text;
        }
        return text.substring(0, LOG_PREVIEW_MAX_CHARS) + "...";
    }

}
