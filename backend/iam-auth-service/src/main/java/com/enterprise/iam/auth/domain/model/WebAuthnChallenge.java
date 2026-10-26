package com.enterprise.iam.auth.domain.model;

import java.time.Instant;
import java.util.Objects;

public record WebAuthnChallenge(
        String challengeId,
        long tenantId,
        Long userId,
        String username,
        WebAuthnCeremonyType ceremonyType,
        String challengeB64,
        String rpId,
        String status,
        Instant expiresAt,
        Instant createdAt) {

    public WebAuthnChallenge {
        Objects.requireNonNull(challengeId, "challengeId must not be null");
        Objects.requireNonNull(ceremonyType, "ceremonyType must not be null");
        Objects.requireNonNull(challengeB64, "challengeB64 must not be null");
        Objects.requireNonNull(rpId, "rpId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public boolean isPendingAndValidAt(Instant now) {
        return "PENDING".equals(status) && expiresAt.isAfter(now);
    }
}
