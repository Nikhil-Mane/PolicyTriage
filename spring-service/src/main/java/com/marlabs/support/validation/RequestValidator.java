package com.marlabs.support.validation;

import com.marlabs.support.exception.InvalidRequestException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Component;

@Component
public class RequestValidator {

    public LocalDate parseAsOf(String asOf) {
        if (asOf == null || asOf.isBlank()) {
            throw new InvalidRequestException("INVALID_AS_OF", "as_of is required and must be an ISO date (YYYY-MM-DD)");
        }
        try {
            return LocalDate.parse(asOf);
        } catch (DateTimeParseException e) {
            throw new InvalidRequestException("INVALID_AS_OF", "as_of must be an ISO date (YYYY-MM-DD): " + asOf);
        }
    }

    public void requireNonEmptyQuestion(String question) {
        if (question == null || question.isBlank()) {
            throw new InvalidRequestException("EMPTY_QUESTION", "question must not be empty");
        }
    }
}
