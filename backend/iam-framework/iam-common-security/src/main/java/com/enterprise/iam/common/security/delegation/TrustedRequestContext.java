package com.enterprise.iam.common.security.delegation;

import java.time.Instant;
import java.util.Objects;

public record TrustedRequestContext(
        long tenantId,
        long subjectId,
        long sessionId,
        String tokenId,
        String requestId,
        Instant expiresAt) {

    public TrustedRequestContext {
        requirePositive(tenantId, "tenantId");
        requirePositive(subjectId, "subjectId");
        requirePositive(sessionId, "sessionId");
        tokenId = requireText(tokenId, "tokenId", 128);
        requestId = requireText(requestId, "requestId", 128);
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static String requireText(String value, String name, int maximumLength) {
        Objects.requireNonNull(value, name + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > maximumLength) {
            throw new IllegalArgumentException(name + " must be non-blank and at most " + maximumLength);
        }
        return normalized;
    }
}
