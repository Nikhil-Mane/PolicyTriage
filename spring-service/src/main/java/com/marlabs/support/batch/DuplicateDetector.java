package com.marlabs.support.batch;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * Stateless byte-hashing helper for exact-duplicate detection within a
 * batch. Callers track "hash already seen" state themselves, scoped to a
 * single batch request (this bean carries no per-batch state itself).
 */
@Component
public class DuplicateDetector {

    public String hash(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
