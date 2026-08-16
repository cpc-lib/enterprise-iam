package com.enterprise.iam.common.security.delegation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DelegationTokenPolicyTest {

    private static final Instant NOW = Instant.parse("2026-08-12T12:00:00Z");
    private DelegationTokenPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new DelegationTokenPolicy(
                Clock.fixed(NOW, ZoneOffset.UTC),
                Set.of("ES256"),
                "iam-gateway",
                "iam-authorization-service",
                Duration.ofSeconds(30),
                Duration.ofSeconds(5));
    }

    @Test
    void acceptsVerifiedShortLivedAudienceBoundContext() {
        DelegationValidationResult result = policy.validate(validClaims());

        assertThat(result.isValid()).isTrue();
        assertThat(result.trustedContext()).get().satisfies(context -> {
            assertThat(context.tenantId()).isEqualTo(10);
            assertThat(context.subjectId()).isEqualTo(20);
            assertThat(context.sessionId()).isEqualTo(30);
            assertThat(context.requestId()).isEqualTo("request-0001");
        });
    }

    @Test
    void rejectsDecodedButUnverifiedToken() {
        DelegationTokenClaims claims = copy(validClaims(), false, "ES256", "iam-delegation+jwt", "key-1",
                "iam-gateway", Set.of("iam-authorization-service"), NOW.minusSeconds(1), NOW.minusSeconds(1), NOW.plusSeconds(20));

        assertFailure(claims, DelegationValidationFailure.INVALID_SIGNATURE);
    }

    @Test
    void rejectsAlgorithmConfusionAndWrongType() {
        assertFailure(
                copy(validClaims(), true, "none", "iam-delegation+jwt", "key-1", "iam-gateway",
                        Set.of("iam-authorization-service"), NOW.minusSeconds(1), NOW.minusSeconds(1), NOW.plusSeconds(20)),
                DelegationValidationFailure.ALGORITHM_NOT_ALLOWED);
        assertFailure(
                copy(validClaims(), true, "ES256", "at+jwt", "key-1", "iam-gateway",
                        Set.of("iam-authorization-service"), NOW.minusSeconds(1), NOW.minusSeconds(1), NOW.plusSeconds(20)),
                DelegationValidationFailure.INVALID_TYPE);
    }

    @Test
    void rejectsWrongAudience() {
        DelegationTokenClaims claims = copy(validClaims(), true, "ES256", "iam-delegation+jwt", "key-1",
                "iam-gateway", Set.of("iam-file-service"), NOW.minusSeconds(1), NOW.minusSeconds(1), NOW.plusSeconds(20));

        assertFailure(claims, DelegationValidationFailure.INVALID_AUDIENCE);
    }

    @Test
    void rejectsMultipleAudiencesEvenWhenExpectedServiceIsIncluded() {
        DelegationTokenClaims claims = copy(
                validClaims(),
                true,
                "ES256",
                "iam-delegation+jwt",
                "key-1",
                "iam-gateway",
                Set.of("iam-authorization-service", "iam-file-service"),
                NOW.minusSeconds(1),
                NOW.minusSeconds(1),
                NOW.plusSeconds(20));

        assertFailure(claims, DelegationValidationFailure.INVALID_AUDIENCE);
    }

    @Test
    void rejectsExcessiveTtl() {
        DelegationTokenClaims claims = copy(validClaims(), true, "ES256", "iam-delegation+jwt", "key-1",
                "iam-gateway", Set.of("iam-authorization-service"), NOW.minusSeconds(1), NOW.minusSeconds(1), NOW.plusSeconds(31));

        assertFailure(claims, DelegationValidationFailure.TOKEN_TTL_EXCEEDED);
    }

    @Test
    void rejectsExpiredOrFutureTokenOutsideClockSkew() {
        assertFailure(
                copy(validClaims(), true, "ES256", "iam-delegation+jwt", "key-1", "iam-gateway",
                        Set.of("iam-authorization-service"), NOW.minusSeconds(30), NOW.minusSeconds(30), NOW.minusSeconds(6)),
                DelegationValidationFailure.TOKEN_EXPIRED);
        assertFailure(
                copy(validClaims(), true, "ES256", "iam-delegation+jwt", "key-1", "iam-gateway",
                        Set.of("iam-authorization-service"), NOW, NOW.plusSeconds(6), NOW.plusSeconds(25)),
                DelegationValidationFailure.TOKEN_NOT_YET_VALID);
    }

    @Test
    void rejectsMissingTenantSubjectOrSessionContext() {
        DelegationTokenClaims original = validClaims();
        DelegationTokenClaims claims = new DelegationTokenClaims(
                true,
                original.algorithm(),
                original.type(),
                original.keyId(),
                original.issuer(),
                original.audience(),
                0,
                original.subjectId(),
                original.sessionId(),
                original.tokenId(),
                original.requestId(),
                original.issuedAt(),
                original.notBefore(),
                original.expiresAt());

        assertFailure(claims, DelegationValidationFailure.MISSING_REQUIRED_CONTEXT);
    }

    private DelegationTokenClaims validClaims() {
        return new DelegationTokenClaims(
                true,
                "ES256",
                "iam-delegation+jwt",
                "key-1",
                "iam-gateway",
                Set.of("iam-authorization-service"),
                10,
                20,
                30,
                "token-0001",
                "request-0001",
                NOW.minusSeconds(1),
                NOW.minusSeconds(1),
                NOW.plusSeconds(20));
    }

    private DelegationTokenClaims copy(
            DelegationTokenClaims original,
            boolean signatureVerified,
            String algorithm,
            String type,
            String keyId,
            String issuer,
            Set<String> audience,
            Instant issuedAt,
            Instant notBefore,
            Instant expiresAt) {
        return new DelegationTokenClaims(
                signatureVerified,
                algorithm,
                type,
                keyId,
                issuer,
                audience,
                original.tenantId(),
                original.subjectId(),
                original.sessionId(),
                original.tokenId(),
                original.requestId(),
                issuedAt,
                notBefore,
                expiresAt);
    }

    private void assertFailure(DelegationTokenClaims claims, DelegationValidationFailure expected) {
        DelegationValidationResult result = policy.validate(claims);

        assertThat(result.isValid()).isFalse();
        assertThat(result.failure()).isEqualTo(expected);
        assertThat(result.trustedContext()).isEmpty();
    }
}
