package com.enterprise.iam.auth.domain.model;

import java.time.Instant;
import java.util.Objects;

public record WebAuthnCredential(
        long id,
        long tenantId,
        long userId,
        String credentialId,
        String publicKeyCose,
        long signCount,
        String aaguid,
        String transports,
        String deviceName,
        WebAuthnCredentialStatus status,
        Instant createdAt,
        Instant lastUsedAt) {

    public WebAuthnCredential {
        Objects.requireNonNull(credentialId, "credentialId must not be null");
        Objects.requireNonNull(publicKeyCose, "publicKeyCose must not be null");
        Objects.requireNonNull(deviceName, "deviceName must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
