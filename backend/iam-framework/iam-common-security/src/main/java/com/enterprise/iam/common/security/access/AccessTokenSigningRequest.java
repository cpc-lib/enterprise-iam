package com.enterprise.iam.common.security.access;

public record AccessTokenSigningRequest(
        long tenantId,
        long subjectId,
        long sessionId,
        long tokenVersion,
        long sessionVersion) {

    public AccessTokenSigningRequest {
        requirePositive(tenantId, "tenantId");
        requirePositive(subjectId, "subjectId");
        requirePositive(sessionId, "sessionId");
        requirePositive(tokenVersion, "tokenVersion");
        requirePositive(sessionVersion, "sessionVersion");
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
