package com.marlabs.support.model;

import java.util.List;

public record BatchResultItem(
        String documentId,
        String processingStatus,
        ExtractedFields extracted,
        FieldEvidence fieldEvidence,
        AnswerResponse policy,
        boolean reviewRequired,
        List<String> issues,
        String duplicateOf,
        ErrorResponse.ErrorDetail error
) {
}
