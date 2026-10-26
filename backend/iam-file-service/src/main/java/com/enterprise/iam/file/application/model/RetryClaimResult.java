package com.enterprise.iam.file.application.model;

import java.util.List;

/**
 * Outcome of one retry-claim tick: the scans claimed because their backoff has elapsed,
 * plus the count of files whose attempts are exhausted and are no longer claimable
 * (surfaced via log/metric).
 */
public record RetryClaimResult(List<ClaimedScan> claimed, int exhaustedCount) {

    public RetryClaimResult {
        claimed = claimed == null ? List.of() : List.copyOf(claimed);
    }
}
