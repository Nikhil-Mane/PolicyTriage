package com.marlabs.support.client;

import com.marlabs.support.model.AnswerResponse;
import com.marlabs.support.model.ErrorResponse;
import com.marlabs.support.model.ExtractedFields;
import com.marlabs.support.model.FieldEvidence;
import java.util.List;

/** Deserialized body of Python's {@code POST /internal/process-document} response. */
public record PythonProcessDocumentResult(
        String processingStatus,
        ExtractedFields extracted,
        FieldEvidence fieldEvidence,
        AnswerResponse policy,
        boolean reviewRequired,
        List<String> issues,
        ErrorResponse.ErrorDetail error
) {
}
