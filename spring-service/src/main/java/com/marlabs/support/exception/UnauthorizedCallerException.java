package com.marlabs.support.exception;

/** Missing or unknown {@code X-Caller-Id} header. Maps to HTTP 401. */
public class UnauthorizedCallerException extends RuntimeException {

    private final String code;

    public UnauthorizedCallerException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
