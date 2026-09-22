package com.marlabs.support.batch;

import java.util.List;

/** The JSON metadata part of a {@code POST /batches} multipart request. */
public record BatchManifest(String batchId, String asOf, List<ManifestEntry> documents) {

    public record ManifestEntry(String documentId, String filename) {
    }
}
