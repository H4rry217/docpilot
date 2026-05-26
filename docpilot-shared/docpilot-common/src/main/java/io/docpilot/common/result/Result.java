package io.docpilot.common.result;

import io.docpilot.common.context.RequestConstants;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.slf4j.MDC;

import java.time.Instant;

@Getter
@Setter
@Accessors(chain = true)
public class Result<T> {

    private Integer code;

    private String msg;

    private T data;

    private String requestId;

    private Instant timestamp;

    public Result() {
        this.requestId = MDC.get(RequestConstants.KEY_REQUEST_ID);
        this.timestamp = Instant.now();
    }

    public static <T> Result<T> success(T data) {
        return Result.<T>of(StatusCode.OK).setData(data);
    }

    public static Result<Void> success() {
        return of(StatusCode.OK);
    }

    public static Result<Void> failure(StatusCode statusCode) {
        return failure(statusCode, null);
    }

    public static Result<Void> failure(StatusCode statusCode, String msg) {
        return Result.<Void>of(statusCode).setMsg(msg == null ? statusCode.defaultMsg() : msg);
    }

    public static <T> Result<T> of(StatusCode statusCode) {
        return new Result<T>()
                .setCode(statusCode.code())
                .setMsg(statusCode.defaultMsg());
    }

}
