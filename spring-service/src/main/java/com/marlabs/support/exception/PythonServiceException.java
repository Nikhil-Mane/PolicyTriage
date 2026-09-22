package com.marlabs.support.exception;

/** Python service unreachable or returned a malformed response. Maps to HTTP 502 (for /answer). */
public class PythonServiceException extends RuntimeException {

    public PythonServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public PythonServiceException(String message) {
        super(message);
    }
}
