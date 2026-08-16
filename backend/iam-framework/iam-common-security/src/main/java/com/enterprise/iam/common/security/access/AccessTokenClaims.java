package com.enterprise.iam.common.security.access;

import java.time.Instant;
import java.util.List;

record AccessTokenClaims(
        String issuer,
        List<String> audience,
        long tenantId,
        long subjectId,
        long sessionId,
        long tokenVersion,
        long sessionVersion,
        String tokenId,
        Instant issuedAt,
        Instant notBefore,
        Instant expiresAt) {

    AccessTokenClaims {
        audience = audience == null ? List.of() : List.copyOf(audience);
    }
}
