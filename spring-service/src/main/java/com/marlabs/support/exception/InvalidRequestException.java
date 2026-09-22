package com.marlabs.support.exception;

/** Malformed input (as_of, question, batch manifest). Maps to HTTP 400. */
public class InvalidRequestException extends RuntimeException {

    private final String code;

    public InvalidRequestException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
