package com.marlabs.support.model;

/** Inbound body of {@code POST /answer}. */
public record AnswerRequest(String asOf, String question) {
}
