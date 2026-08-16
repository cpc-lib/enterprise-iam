package com.enterprise.iam.auth.application.model;

import java.util.Arrays;
import java.util.Objects;

/** One-owner refresh-token buffer. It never renders its value in logs. */
public final class SensitiveRefreshToken implements AutoCloseable {

    private char[] value;

    public SensitiveRefreshToken(char[] value) {
        Objects.requireNonNull(value, "value must not be null");
        if (value.length < 32 || value.length > 256) {
            throw new IllegalArgumentException("refresh token length is invalid");
        }
        this.value = value.clone();
    }

    public synchronized char[] copyValue() {
        ensureAvailable();
        return value.clone();
    }

    public synchronized boolean isDestroyed() {
        return value == null;
    }

    public synchronized void destroy() {
        if (value != null) {
            Arrays.fill(value, '\0');
            value = null;
        }
    }

    @Override
    public void close() {
        destroy();
    }

    @Override
    public String toString() {
        return "SensitiveRefreshToken[REDACTED]";
    }

    private void ensureAvailable() {
        if (value == null) {
            throw new IllegalStateException("refresh token has been destroyed");
        }
    }
}
