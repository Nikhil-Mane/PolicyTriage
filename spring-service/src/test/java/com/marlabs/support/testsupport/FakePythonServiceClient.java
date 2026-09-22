package com.marlabs.support.testsupport;

import com.marlabs.support.client.PythonProcessDocumentResult;
import com.marlabs.support.client.PythonServiceClient;
import com.marlabs.support.exception.PythonServiceException;
import com.marlabs.support.exception.PythonServiceTimeoutException;
import com.marlabs.support.model.AnswerResponse;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Function;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Hand-written test double for {@link PythonServiceClient}, used instead of
 * standing up a real HTTP server. Tests queue canned answer responses, or
 * set a failure mode, then assert on how Spring's controllers/services
 * react.
 */
public class FakePythonServiceClient implements PythonServiceClient {

    public enum Failure { NONE, TIMEOUT, UNAVAILABLE }

    private final Deque<AnswerResponse> queuedAnswers = new ArrayDeque<>();
    private Failure answerFailure = Failure.NONE;
    private Function<String, PythonProcessDocumentResult> documentResponder = documentId ->
            new PythonProcessDocumentResult("COMPLETED", null, null,
                    new AnswerResponse("INSUFFICIENT_EVIDENCE", null, java.util.List.of()), true, java.util.List.of(), null);
    private Failure documentFailure = Failure.NONE;
    public int processDocumentCallCount = 0;

    public void queueAnswer(AnswerResponse response) {
        queuedAnswers.addLast(response);
    }

    public void failNextAnswerWith(Failure failure) {
        this.answerFailure = failure;
    }

    public void respondToDocumentsWith(Function<String, PythonProcessDocumentResult> responder) {
        this.documentResponder = responder;
    }

    public void failDocumentCallsWith(Failure failure) {
        this.documentFailure = failure;
    }

    /** Reset all queued state between tests, since this bean is a shared Spring singleton. */
    public void reset() {
        queuedAnswers.clear();
        answerFailure = Failure.NONE;
        documentFailure = Failure.NONE;
        processDocumentCallCount = 0;
        documentResponder = documentId ->
                new PythonProcessDocumentResult("COMPLETED", null, null,
                        new AnswerResponse("INSUFFICIENT_EVIDENCE", null, java.util.List.of()), true, java.util.List.of(), null);
    }

    @Override
    public AnswerResponse answer(String tenant, String role, LocalDate asOf, String question, String requestId) {
        if (answerFailure == Failure.TIMEOUT) {
            throw new PythonServiceTimeoutException("simulated timeout", null);
        }
        if (answerFailure == Failure.UNAVAILABLE) {
            throw new PythonServiceException("simulated dependency failure");
        }
        if (!queuedAnswers.isEmpty()) {
            return queuedAnswers.pollFirst();
        }
        return new AnswerResponse("INSUFFICIENT_EVIDENCE", null, java.util.List.of());
    }

    @Override
    public PythonProcessDocumentResult processDocument(
            String tenant, String role, LocalDate asOf,
            String batchId, String documentId, String filename, byte[] content) {
        processDocumentCallCount++;
        if (documentFailure == Failure.TIMEOUT) {
            throw new PythonServiceTimeoutException("simulated timeout", null);
        }
        if (documentFailure == Failure.UNAVAILABLE) {
            throw new PythonServiceException("simulated dependency failure");
        }
        return documentResponder.apply(documentId);
    }

    @TestConfiguration
    public static class Config {
        @Bean
        @Primary
        public PythonServiceClient fakePythonServiceClient() {
            return new FakePythonServiceClient();
        }
    }
}
