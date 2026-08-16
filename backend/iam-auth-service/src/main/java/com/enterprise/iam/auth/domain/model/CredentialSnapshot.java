package com.enterprise.iam.auth.domain.model;

import java.time.Instant;
import java.util.Objects;

public record CredentialSnapshot(
        long tenantId,
        long userId,
        String passwordPhc,
        boolean active,
        Instant lockedUntil) {

    public CredentialSnapshot {
        requirePositive(tenantId, "tenantId");
        requirePositive(userId, "userId");
        Objects.requireNonNull(passwordPhc, "passwordPhc must not be null");
        if (passwordPhc.isBlank() || passwordPhc.length() > 512) {
            throw new IllegalArgumentException("passwordPhc must be non-blank and at most 512 characters");
        }
    }

    public boolean lockedAt(Instant instant) {
        return lockedUntil != null && lockedUntil.isAfter(instant);
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
