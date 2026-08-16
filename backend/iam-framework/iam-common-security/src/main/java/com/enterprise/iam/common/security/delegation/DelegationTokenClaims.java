package com.enterprise.iam.common.security.delegation;

import java.time.Instant;
import java.util.Set;

/**
 * Claims produced only after a decoder has parsed the compact token. The
 * signatureVerified flag must represent cryptographic verification, not mere
 * decoding.
 */
public record DelegationTokenClaims(
        boolean signatureVerified,
        String algorithm,
        String type,
        String keyId,
        String issuer,
        Set<String> audience,
        long tenantId,
        long subjectId,
        long sessionId,
        String tokenId,
        String requestId,
        Instant issuedAt,
        Instant notBefore,
        Instant expiresAt) {

    public DelegationTokenClaims {
        audience = audience == null ? Set.of() : Set.copyOf(audience);
    }
}
