package com.enterprise.iam.outbox;

import java.util.Objects;
import java.util.regex.Pattern;

/** Signals deterministic poison data that must be dead-lettered immediately. */
public final class OutboxNonRetryableException extends RuntimeException {

    private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]{2,127}$");

    private final String failureCode;

    public OutboxNonRetryableException(String failureCode, Throwable cause) {
        super("non-retryable outbox delivery failure", cause);
        this.failureCode = Objects.requireNonNull(failureCode, "failureCode must not be null");
        if (!CODE.matcher(failureCode).matches()) {
            throw new IllegalArgumentException("failureCode is invalid");
        }
    }

    public OutboxNonRetryableException(String failureCode) {
        this(failureCode, null);
    }

    public String failureCode() {
        return failureCode;
    }
}
