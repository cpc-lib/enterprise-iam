package com.enterprise.iam.common.security.access;

import java.time.Instant;
import java.util.Objects;

/** Identity and version context published only after cryptographic validation. */
public record VerifiedAccessToken(
        long tenantId,
        long subjectId,
        long sessionId,
        long tokenVersion,
        long sessionVersion,
        String tokenId,
        Instant issuedAt,
        Instant expiresAt) {

    public VerifiedAccessToken {
        requirePositive(tenantId, "tenantId");
        requirePositive(subjectId, "subjectId");
        requirePositive(sessionId, "sessionId");
        requirePositive(tokenVersion, "tokenVersion");
        requirePositive(sessionVersion, "sessionVersion");
        Objects.requireNonNull(tokenId, "tokenId must not be null");
        Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
