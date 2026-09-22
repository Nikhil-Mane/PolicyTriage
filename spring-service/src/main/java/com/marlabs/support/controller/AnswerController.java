package com.marlabs.support.controller;

import com.marlabs.support.caller.CallerIdentity;
import com.marlabs.support.caller.CallerRegistry;
import com.marlabs.support.client.PythonServiceClient;
import com.marlabs.support.exception.UnauthorizedCallerException;
import com.marlabs.support.model.AnswerRequest;
import com.marlabs.support.model.AnswerResponse;
import com.marlabs.support.validation.RequestValidator;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnswerController {

    private static final Logger log = LoggerFactory.getLogger(AnswerController.class);

    private final CallerRegistry callerRegistry;
    private final RequestValidator requestValidator;
    private final PythonServiceClient pythonServiceClient;

    public AnswerController(CallerRegistry callerRegistry, RequestValidator requestValidator, PythonServiceClient pythonServiceClient) {
        this.callerRegistry = callerRegistry;
        this.requestValidator = requestValidator;
        this.pythonServiceClient = pythonServiceClient;
    }

    @PostMapping("/answer")
    public AnswerResponse answer(@RequestHeader(value = "X-Caller-Id", required = false) String callerId, @RequestBody AnswerRequest request) {
        CallerIdentity caller = callerRegistry.lookup(callerId)
                .orElseThrow(() -> new UnauthorizedCallerException("UNKNOWN_CALLER", "missing or unknown X-Caller-Id"));

        LocalDate asOf = requestValidator.parseAsOf(request.asOf());
        requestValidator.requireNonEmptyQuestion(request.question());

        String requestId = UUID.randomUUID().toString();
        log.info("request_id={} tenant={} role={}", requestId, caller.tenant(), caller.role());

        return pythonServiceClient.answer(caller.tenant(), caller.role(), asOf, request.question(), requestId);
    }
}
