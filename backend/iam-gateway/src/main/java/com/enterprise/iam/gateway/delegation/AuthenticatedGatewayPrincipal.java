package com.enterprise.iam.gateway.delegation;

import java.util.Objects;

/** Context produced only after access-token and authoritative session validation. */
public record AuthenticatedGatewayPrincipal(
        long tenantId,
        long subjectId,
        long sessionId,
        String requestId) {

    public AuthenticatedGatewayPrincipal {
        requirePositive(tenantId, "tenantId");
        requirePositive(subjectId, "subjectId");
        requirePositive(sessionId, "sessionId");
        Objects.requireNonNull(requestId, "requestId must not be null");
        if (!requestId.matches("[A-Za-z0-9._:-]{8,128}")) {
            throw new IllegalArgumentException("requestId format is invalid");
        }
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
