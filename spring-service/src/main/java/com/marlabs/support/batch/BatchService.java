package com.marlabs.support.batch;

import com.marlabs.support.caller.CallerIdentity;
import com.marlabs.support.client.PythonProcessDocumentResult;
import com.marlabs.support.client.PythonServiceClient;
import com.marlabs.support.exception.PythonServiceException;
import com.marlabs.support.exception.PythonServiceTimeoutException;
import com.marlabs.support.model.BatchResponse;
import com.marlabs.support.model.BatchResultItem;
import com.marlabs.support.model.ErrorResponse;
import com.marlabs.support.validation.RequestValidator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the batch intake pipeline: validate manifest, detect exact
 * byte-duplicates, call Python once per document with each call
 * independently isolated so a single document's failure never sinks the
 * batch, then assemble the response preserving manifest order.
 */
@Service
public class BatchService {

    private static final Logger log = LoggerFactory.getLogger(BatchService.class);

    private final RequestValidator requestValidator;
    private final ManifestValidator manifestValidator;
    private final DuplicateDetector duplicateDetector;
    private final PythonServiceClient pythonServiceClient;

    public BatchService(
            RequestValidator requestValidator,
            ManifestValidator manifestValidator,
            DuplicateDetector duplicateDetector,
            PythonServiceClient pythonServiceClient) {
        this.requestValidator = requestValidator;
        this.manifestValidator = manifestValidator;
        this.duplicateDetector = duplicateDetector;
        this.pythonServiceClient = pythonServiceClient;
    }

    public BatchResponse processBatch(CallerIdentity caller, BatchManifest manifest, Map<String, byte[]> filesByName) {
        LocalDate asOf = requestValidator.parseAsOf(manifest.asOf());
        manifestValidator.validate(manifest, filesByName.keySet());

        Map<String, String> seenHashToDocumentId = new HashMap<>();
        List<BatchResultItem> results = new ArrayList<>();

        for (BatchManifest.ManifestEntry entry : manifest.documents()) {
            byte[] content = filesByName.get(entry.filename());
            String hash = duplicateDetector.hash(content);
            String duplicateOf = seenHashToDocumentId.putIfAbsent(hash, entry.documentId());

            results.add(processOne(caller, manifest.batchId(), entry, asOf, content, duplicateOf));
        }

        int completed = (int) results.stream().filter(r -> "COMPLETED".equals(r.processingStatus())).count();
        int failed = results.size() - completed;
        log.info("batch_id={} total={} completed={} failed={}", manifest.batchId(), results.size(), completed, failed);

        return new BatchResponse(manifest.batchId(), new BatchResponse.Summary(results.size(), completed, failed), results);
    }

    private BatchResultItem processOne(
            CallerIdentity caller, String batchId, BatchManifest.ManifestEntry entry,
            LocalDate asOf, byte[] content, String duplicateOf) {
        try {
            PythonProcessDocumentResult result = pythonServiceClient.processDocument(
                    caller.tenant(), caller.role(), asOf, batchId, entry.documentId(), entry.filename(), content);

            return new BatchResultItem(
                    entry.documentId(), result.processingStatus(), result.extracted(), result.fieldEvidence(),
                    result.policy(), result.reviewRequired(), result.issues(), duplicateOf, result.error());
        } catch (PythonServiceTimeoutException e) {
            log.warn("batch_id={} document_id={} failed: timeout", batchId, entry.documentId());
            return failedItem(entry.documentId(), duplicateOf, "MODEL_TIMEOUT", "Python service call timed out");
        } catch (PythonServiceException e) {
            log.warn("batch_id={} document_id={} failed: {}", batchId, entry.documentId(), e.getMessage());
            return failedItem(entry.documentId(), duplicateOf, "MODEL_UNAVAILABLE", "Python service call failed");
        }
    }

    private BatchResultItem failedItem(String documentId, String duplicateOf, String code, String message) {
        return new BatchResultItem(
                documentId, "FAILED", null, null, null, true, List.of(), duplicateOf,
                new ErrorResponse.ErrorDetail(code, message));
    }
}
