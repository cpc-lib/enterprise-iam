package com.enterprise.iam.common.core.context;

import java.util.Objects;

/**
 * Correlation identifier propagated through synchronous calls, events and
 * audit records. This technical value object intentionally contains no
 * business-domain semantics.
 */
public record TraceId(String value) {

    public static final int MAX_LENGTH = 128;

    public TraceId {
        Objects.requireNonNull(value, "value must not be null");
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("value must not exceed " + MAX_LENGTH + " characters");
        }
    }

    public static TraceId of(String value) {
        return new TraceId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
