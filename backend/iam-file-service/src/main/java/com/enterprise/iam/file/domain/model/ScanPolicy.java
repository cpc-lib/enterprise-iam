package com.enterprise.iam.file.domain.model;

/**
 * Scan policy for a tenant (SPEC 35 §11/§15). Framework-free domain value object.
 *
 * @param scanRequired        whether files must be scanned before becoming available
 * @param blockOnScanFailure  whether scanner failures keep the file unavailable (never auto-release)
 * @param maxAttempts         bounded number of scan attempts before the file is surfaced as exhausted
 */
public record ScanPolicy(boolean scanRequired, boolean blockOnScanFailure, int maxAttempts) {

    public static final int DEFAULT_MAX_ATTEMPTS = 3;

    public ScanPolicy {
        if (maxAttempts < 1 || maxAttempts > 20) {
            throw new IllegalArgumentException("maxAttempts must be between 1 and 20");
        }
    }

    public static ScanPolicy defaultPolicy() {
        return new ScanPolicy(true, true, DEFAULT_MAX_ATTEMPTS);
    }
}
