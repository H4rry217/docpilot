package io.docpilot.common.result;

public enum StatusCode {

    OK(0, 200, "OK"),
    BAD_REQUEST(40000, 400, "Bad request"),
    UNAUTHORIZED(40100, 401, "Authentication is required"),
    FORBIDDEN(40300, 403, "Access denied"),
    NOT_FOUND(40400, 404, "Resource not found"),
    CONFLICT(40900, 409, "Resource conflict"),
    BUSINESS_ERROR(50000, 500, "Business error"),
    SYSTEM_ERROR(50001, 500, "Internal server error");

    private final int code;
    private final int httpStatus;
    private final String defaultMsg;

    StatusCode(int code, int httpStatus, String defaultMsg) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMsg = defaultMsg;
    }

    public int code() {
        return code;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String defaultMsg() {
        return defaultMsg;
    }

}
