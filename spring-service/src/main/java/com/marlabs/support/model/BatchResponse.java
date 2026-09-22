package com.marlabs.support.model;

import java.util.List;

public record BatchResponse(String batchId, Summary summary, List<BatchResultItem> results) {

    public record Summary(int total, int completed, int failed) {
    }
}
