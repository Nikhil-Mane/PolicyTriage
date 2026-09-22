package com.marlabs.support.batch;

import com.marlabs.support.exception.InvalidRequestException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Validates a batch manifest against the multipart file parts actually
 * uploaded. Any violation fails the whole batch (400) before any document
 * is sent to Python.
 */
@Component
public class ManifestValidator {

    public void validate(BatchManifest manifest, Set<String> uploadedFilenames) {
        if (manifest.documents() == null || manifest.documents().isEmpty()) {
            throw new InvalidRequestException("INVALID_BATCH_METADATA", "manifest must list at least one document");
        }

        Set<String> seenIds = new HashSet<>();
        Set<String> seenFilenames = new HashSet<>();
        for (BatchManifest.ManifestEntry entry : manifest.documents()) {
            if (entry.documentId() == null || entry.documentId().isBlank() || entry.filename() == null || entry.filename().isBlank()) {
                throw new InvalidRequestException("INVALID_BATCH_METADATA", "each manifest entry requires document_id and filename");
            }
            if (!seenIds.add(entry.documentId())) {
                throw new InvalidRequestException("DUPLICATE_MANIFEST_ID", "duplicate document_id: " + entry.documentId());
            }
            seenFilenames.add(entry.filename());
        }

        List<String> manifestFilenames = manifest.documents().stream().map(BatchManifest.ManifestEntry::filename).toList();
        Set<String> missing = new HashSet<>(manifestFilenames);
        missing.removeAll(uploadedFilenames);
        if (!missing.isEmpty()) {
            throw new InvalidRequestException("MANIFEST_FILE_MISMATCH", "missing file parts for: " + missing);
        }

        Set<String> extra = new HashSet<>(uploadedFilenames);
        extra.removeAll(seenFilenames);
        if (!extra.isEmpty()) {
            throw new InvalidRequestException("MANIFEST_FILE_MISMATCH", "unexpected file parts not in manifest: " + extra);
        }
    }
}
