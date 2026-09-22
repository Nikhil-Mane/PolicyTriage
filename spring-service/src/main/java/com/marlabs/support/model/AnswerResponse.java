package com.marlabs.support.model;

import java.util.List;

/** Shape shared verbatim by {@code POST /answer} and each batch item's {@code policy} field. */
public record AnswerResponse(String status, String answer, List<Citation> citations) {
}
