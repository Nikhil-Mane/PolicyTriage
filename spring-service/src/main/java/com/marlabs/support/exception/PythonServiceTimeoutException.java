package com.marlabs.support.exception;

/** The bounded call to Python exceeded its timeout. Maps to HTTP 504 (for /answer). No retry is attempted. */
public class PythonServiceTimeoutException extends RuntimeException {

    public PythonServiceTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
