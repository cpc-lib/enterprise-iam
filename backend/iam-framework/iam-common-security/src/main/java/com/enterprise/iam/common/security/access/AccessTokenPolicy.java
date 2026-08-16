package com.enterprise.iam.common.security.access;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public final class AccessTokenPolicy {

    public static final String REQUIRED_TYPE = "at+jwt";
    private static final Pattern TOKEN_ID = Pattern.compile("^[A-Za-z0-9._:-]{8,128}$");
    private static final Pattern SERVICE_NAME = Pattern.compile("^[a-z][a-z0-9-]{2,63}$");

    private final Clock clock;
    private final String expectedIssuer;
    private final String expectedAudience;
    private final Duration maximumTtl;
    private final Duration clockSkew;

    public AccessTokenPolicy(
            Clock clock,
            String expectedIssuer,
            String expectedAudience,
            Duration maximumTtl,
            Duration clockSkew) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.expectedIssuer = requireServiceName(expectedIssuer, "expectedIssuer");
        this.expectedAudience = requireServiceName(expectedAudience, "expectedAudience");
        this.maximumTtl = requirePositive(maximumTtl, "maximumTtl");
        this.clockSkew = requireNonNegative(clockSkew, "clockSkew");
        if (this.maximumTtl.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalArgumentException("maximumTtl must not exceed 5 minutes");
        }
        if (this.clockSkew.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("clockSkew must not exceed 30 seconds");
        }
    }

    AccessTokenValidationResult validate(AccessTokenClaims claims) {
        Objects.requireNonNull(claims, "claims must not be null");
        if (!expectedIssuer.equals(claims.issuer())) {
            return invalid(AccessTokenValidationFailure.INVALID_ISSUER);
        }
        if (!List.of(expectedAudience).equals(claims.audience())) {
            return invalid(AccessTokenValidationFailure.INVALID_AUDIENCE);
        }
        if (claims.issuedAt() == null
                || claims.notBefore() == null
                || claims.expiresAt() == null
                || !claims.expiresAt().isAfter(claims.issuedAt())
                || !claims.expiresAt().isAfter(claims.notBefore())
                || claims.notBefore().isBefore(claims.issuedAt().minus(clockSkew))) {
            return invalid(AccessTokenValidationFailure.INVALID_TIME_CLAIMS);
        }
        if (Duration.between(claims.issuedAt(), claims.expiresAt())
                .compareTo(maximumTtl) > 0) {
            return invalid(AccessTokenValidationFailure.TOKEN_TTL_EXCEEDED);
        }

        Instant now = clock.instant();
        if (claims.issuedAt().isAfter(now.plus(clockSkew))) {
            return invalid(AccessTokenValidationFailure.INVALID_TIME_CLAIMS);
        }
        if (now.plus(clockSkew).isBefore(claims.notBefore())) {
            return invalid(AccessTokenValidationFailure.TOKEN_NOT_YET_VALID);
        }
        if (!now.minus(clockSkew).isBefore(claims.expiresAt())) {
            return invalid(AccessTokenValidationFailure.TOKEN_EXPIRED);
        }
        if (claims.tenantId() <= 0
                || claims.subjectId() <= 0
                || claims.sessionId() <= 0
                || claims.tokenVersion() <= 0
                || claims.sessionVersion() <= 0
                || claims.tokenId() == null
                || !TOKEN_ID.matcher(claims.tokenId()).matches()) {
            return invalid(AccessTokenValidationFailure.MISSING_REQUIRED_CONTEXT);
        }
        return AccessTokenValidationResult.valid(new VerifiedAccessToken(
                claims.tenantId(),
                claims.subjectId(),
                claims.sessionId(),
                claims.tokenVersion(),
                claims.sessionVersion(),
                claims.tokenId(),
                claims.issuedAt(),
                claims.expiresAt()));
    }

    private static AccessTokenValidationResult invalid(AccessTokenValidationFailure failure) {
        return AccessTokenValidationResult.invalid(failure);
    }

    private static String requireServiceName(String value, String name) {
        if (value == null || !SERVICE_NAME.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " format is invalid");
        }
        return value;
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
