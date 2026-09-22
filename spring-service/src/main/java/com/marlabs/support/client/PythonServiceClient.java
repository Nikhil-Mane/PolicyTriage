package com.marlabs.support.client;

import com.marlabs.support.model.AnswerResponse;
import java.time.LocalDate;

/**
 * A single bounded, non-retried call per method to the internal Python
 * service. Tests fake this interface instead of standing up real HTTP.
 */
public interface PythonServiceClient {

    AnswerResponse answer(String tenant, String role, LocalDate asOf, String question, String requestId);

    PythonProcessDocumentResult processDocument(
            String tenant, String role, LocalDate asOf,
            String batchId, String documentId, String filename, byte[] content);
}
