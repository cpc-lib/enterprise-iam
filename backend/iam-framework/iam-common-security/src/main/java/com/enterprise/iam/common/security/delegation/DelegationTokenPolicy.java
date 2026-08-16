package com.enterprise.iam.common.security.delegation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public final class DelegationTokenPolicy {

    public static final String REQUIRED_TYPE = "iam-delegation+jwt";

    private final Clock clock;
    private final Set<String> allowedAlgorithms;
    private final String expectedIssuer;
    private final String expectedAudience;
    private final Duration maximumTtl;
    private final Duration clockSkew;

    public DelegationTokenPolicy(
            Clock clock,
            Set<String> allowedAlgorithms,
            String expectedIssuer,
            String expectedAudience,
            Duration maximumTtl,
            Duration clockSkew) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.allowedAlgorithms = Set.copyOf(allowedAlgorithms);
        this.expectedIssuer = requireText(expectedIssuer, "expectedIssuer");
        this.expectedAudience = requireText(expectedAudience, "expectedAudience");
        this.maximumTtl = requirePositive(maximumTtl, "maximumTtl");
        this.clockSkew = requireNonNegative(clockSkew, "clockSkew");
        if (this.allowedAlgorithms.isEmpty()
                || this.allowedAlgorithms.stream().anyMatch("none"::equalsIgnoreCase)) {
            throw new IllegalArgumentException("allowedAlgorithms must be non-empty and exclude none");
        }
    }

    public DelegationValidationResult validate(DelegationTokenClaims claims) {
        Objects.requireNonNull(claims, "claims must not be null");
        if (!claims.signatureVerified()) {
            return invalid(DelegationValidationFailure.INVALID_SIGNATURE);
        }
        if (!allowedAlgorithms.contains(claims.algorithm())) {
            return invalid(DelegationValidationFailure.ALGORITHM_NOT_ALLOWED);
        }
        if (!REQUIRED_TYPE.equals(claims.type())) {
            return invalid(DelegationValidationFailure.INVALID_TYPE);
        }
        if (isBlank(claims.keyId())) {
            return invalid(DelegationValidationFailure.MISSING_KEY_ID);
        }
        if (!expectedIssuer.equals(claims.issuer())) {
            return invalid(DelegationValidationFailure.INVALID_ISSUER);
        }
        if (!Set.of(expectedAudience).equals(claims.audience())) {
            return invalid(DelegationValidationFailure.INVALID_AUDIENCE);
        }
        if (claims.issuedAt() == null || claims.notBefore() == null || claims.expiresAt() == null
                || !claims.expiresAt().isAfter(claims.issuedAt())
                || !claims.expiresAt().isAfter(claims.notBefore())
                || claims.notBefore().isBefore(claims.issuedAt().minus(clockSkew))) {
            return invalid(DelegationValidationFailure.INVALID_TIME_CLAIMS);
        }
        if (Duration.between(claims.issuedAt(), claims.expiresAt()).compareTo(maximumTtl) > 0) {
            return invalid(DelegationValidationFailure.TOKEN_TTL_EXCEEDED);
        }

        Instant now = clock.instant();
        if (now.plus(clockSkew).isBefore(claims.notBefore())) {
            return invalid(DelegationValidationFailure.TOKEN_NOT_YET_VALID);
        }
        if (!now.minus(clockSkew).isBefore(claims.expiresAt())) {
            return invalid(DelegationValidationFailure.TOKEN_EXPIRED);
        }
        if (claims.tenantId() <= 0
                || claims.subjectId() <= 0
                || claims.sessionId() <= 0
                || isBlank(claims.tokenId())
                || isBlank(claims.requestId())) {
            return invalid(DelegationValidationFailure.MISSING_REQUIRED_CONTEXT);
        }
        return DelegationValidationResult.valid(new TrustedRequestContext(
                claims.tenantId(),
                claims.subjectId(),
                claims.sessionId(),
                claims.tokenId(),
                claims.requestId(),
                claims.expiresAt()));
    }

    private static DelegationValidationResult invalid(DelegationValidationFailure failure) {
        return DelegationValidationResult.invalid(failure);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String requireText(String value, String name) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static Duration requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static Duration requireNonNegative(Duration value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }
}
