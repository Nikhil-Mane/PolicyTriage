package com.marlabs.support.exception;

import com.marlabs.support.model.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UnauthorizedCallerException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedCallerException ex) {
        log.info("rejected request: code={}", ex.getCode());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorResponse.of(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(InvalidRequestException ex) {
        log.info("rejected request: code={}", ex.getCode());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.of(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedBody(HttpMessageNotReadableException ex) {
        log.info("rejected request: code=MALFORMED_REQUEST_BODY");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.of("MALFORMED_REQUEST_BODY", "request body could not be parsed"));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException ex) {
        log.info("rejected request: code=MISSING_REQUEST_PART part={}", ex.getRequestPartName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.of("MISSING_REQUEST_PART", ex.getMessage()));
    }

    @ExceptionHandler(PythonServiceTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleTimeout(PythonServiceTimeoutException ex) {
        log.warn("dependency timeout: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(ErrorResponse.of("DEPENDENCY_TIMEOUT", ex.getMessage()));
    }

    @ExceptionHandler(PythonServiceException.class)
    public ResponseEntity<ErrorResponse> handleDependencyFailure(PythonServiceException ex) {
        log.warn("dependency failure: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ErrorResponse.of("DEPENDENCY_UNAVAILABLE", ex.getMessage()));
    }
}
