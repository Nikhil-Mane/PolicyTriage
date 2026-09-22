package com.marlabs.support.client;

import com.marlabs.support.exception.PythonServiceException;
import com.marlabs.support.exception.PythonServiceTimeoutException;
import com.marlabs.support.model.AnswerResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class PythonServiceHttpClient implements PythonServiceClient {

    private final WebClient webClient;
    private final Duration timeout;

    public PythonServiceHttpClient(
            WebClient.Builder webClientBuilder,
            @Value("${python-service.base-url}") String baseUrl,
            @Value("${python-service.timeout-ms}") long timeoutMs) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.timeout = Duration.ofMillis(timeoutMs);
    }

    private record AnswerPayload(String tenant, String role, String asOf, String question) {
    }

    @Override
    public AnswerResponse answer(String tenant, String role, LocalDate asOf, String question, String requestId) {
        try {
            return webClient.post()
                    .uri("/internal/answer")
                    .header("X-Request-Id", requestId)
                    .bodyValue(new AnswerPayload(tenant, role, asOf.toString(), question))
                    .retrieve()
                    .bodyToMono(AnswerResponse.class)
                    .timeout(timeout)
                    .block();
        } catch (Exception e) {
            throw translate(e);
        }
    }

    @Override
    public PythonProcessDocumentResult processDocument(
            String tenant, String role, LocalDate asOf,
            String batchId, String documentId, String filename, byte[] content) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("tenant", tenant);
        body.part("role", role);
        body.part("as_of", asOf.toString());
        body.part("file", content).filename(filename);

        try {
            return webClient.post()
                    .uri("/internal/process-document")
                    .header("X-Batch-Id", batchId)
                    .header("X-Document-Id", documentId)
                    .contentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA)
                    .bodyValue(body.build())
                    .retrieve()
                    .bodyToMono(PythonProcessDocumentResult.class)
                    .timeout(timeout)
                    .block();
        } catch (Exception e) {
            throw translate(e);
        }
    }

    private RuntimeException translate(Exception e) {
        if (e instanceof PythonServiceException || e instanceof PythonServiceTimeoutException) {
            return (RuntimeException) e;
        }
        if (e instanceof TimeoutException || e.getCause() instanceof TimeoutException) {
            return new PythonServiceTimeoutException("Python service call timed out", e);
        }
        return new PythonServiceException("Python service call failed: " + e.getMessage(), e);
    }
}
