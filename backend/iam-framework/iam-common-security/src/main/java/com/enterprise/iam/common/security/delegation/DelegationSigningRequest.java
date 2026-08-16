package com.enterprise.iam.common.security.delegation;

import java.util.Objects;

public record DelegationSigningRequest(
        String audience,
        long tenantId,
        long subjectId,
        long sessionId,
        String requestId) {

    public DelegationSigningRequest {
        audience = requireText(audience, "audience", 128);
        requirePositive(tenantId, "tenantId");
        requirePositive(subjectId, "subjectId");
        requirePositive(sessionId, "sessionId");
        requestId = requireText(requestId, "requestId", 128);
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
