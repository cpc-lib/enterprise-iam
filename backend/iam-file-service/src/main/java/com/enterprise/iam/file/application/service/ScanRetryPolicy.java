package com.enterprise.iam.file.application.service;

import java.time.Duration;
import java.util.Objects;

/**
 * Bounded exponential backoff for scan retries (SPEC 35 §16):
 * {@code backoff(attempt) = min(cap, base * 2^(attempt-1))}.
 */
public final class ScanRetryPolicy {

    private final Duration baseBackoff;
    private final Duration maximumBackoff;

    public ScanRetryPolicy(Duration baseBackoff, Duration maximumBackoff) {
        this.baseBackoff = Objects.requireNonNull(baseBackoff, "baseBackoff must not be null");
        this.maximumBackoff = Objects.requireNonNull(maximumBackoff, "maximumBackoff must not be null");
        if (baseBackoff.isNegative() || baseBackoff.isZero()) {
            throw new IllegalArgumentException("baseBackoff must be positive");
        }
        if (maximumBackoff.compareTo(baseBackoff) < 0) {
            throw new IllegalArgumentException("maximumBackoff must be >= baseBackoff");
        }
    }

    /**
     * Backoff after {@code attempt} failed attempts (1-based): attempt 1 -&gt; base,
     * attempt 2 -&gt; 2*base, attempt 3 -&gt; 4*base, capped at maximumBackoff.
     */
    public Duration backoffFor(int attempt) {
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be >= 1");
        }
        long baseSeconds = baseBackoff.toSeconds();
        int shift = Math.min(attempt - 1, 62);
        long scaled;
        try {
            scaled = Math.multiplyExact(baseSeconds, 1L << shift);
        } catch (ArithmeticException overflow) {
            return maximumBackoff;
        }
        Duration candidate = Duration.ofSeconds(scaled);
        return candidate.compareTo(maximumBackoff) > 0 ? maximumBackoff : candidate;
    }
}
