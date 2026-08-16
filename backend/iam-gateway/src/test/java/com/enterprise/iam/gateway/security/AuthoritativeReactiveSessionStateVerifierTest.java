package com.enterprise.iam.gateway.security;

import com.enterprise.iam.common.security.access.VerifiedAccessToken;
import com.enterprise.iam.common.security.session.SessionProjectionStatus;
import com.enterprise.iam.common.security.session.SessionSecurityProjection;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthoritativeReactiveSessionStateVerifierTest {

    private static final Instant NOW = Instant.parse("2026-08-12T12:00:00Z");

    @Test
    void acceptsOnlyExactActiveUnexpiredSnapshot() {
        var verifier = verifier((tenantId, subjectId, sessionId) -> Mono.just(snapshot(
                4, 5, "ACTIVE", NOW.plusSeconds(60), NOW.plusSeconds(120))));

        assertThat(verifier.verify(token()).block())
                .isEqualTo(SessionStateVerification.ACTIVE);
    }

    @Test
    void deniesMissingRevokedExpiredOrVersionMismatchedSnapshot() {
        assertInvalid((tenantId, subjectId, sessionId) -> Mono.empty());
        assertInvalid((tenantId, subjectId, sessionId) -> Mono.just(snapshot(
                4, 5, "REVOKED", NOW.plusSeconds(60), NOW.plusSeconds(120))));
        assertInvalid((tenantId, subjectId, sessionId) -> Mono.just(snapshot(
                4, 5, "ACTIVE", NOW, NOW.plusSeconds(120))));
        assertInvalid((tenantId, subjectId, sessionId) -> Mono.just(snapshot(
                99, 5, "ACTIVE", NOW.plusSeconds(60), NOW.plusSeconds(120))));
        assertInvalid((tenantId, subjectId, sessionId) -> Mono.just(snapshot(
                4, 99, "ACTIVE", NOW.plusSeconds(60), NOW.plusSeconds(120))));
    }

    @Test
    void propagatesReaderFailureForGatewayToMapTo503() {
        var verifier = verifier((tenantId, subjectId, sessionId) ->
                Mono.error(new IllegalStateException("Redis unavailable")));

        assertThatThrownBy(() -> verifier.verify(token()).block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Redis unavailable");
    }

    private void assertInvalid(ReactiveSessionSnapshotReader reader) {
        assertThat(verifier(reader).verify(token()).block())
                .isEqualTo(SessionStateVerification.INVALID);
    }

    private AuthoritativeReactiveSessionStateVerifier verifier(
            ReactiveSessionSnapshotReader reader) {
        return new AuthoritativeReactiveSessionStateVerifier(
                reader, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static VerifiedAccessToken token() {
        return new VerifiedAccessToken(
                10, 20, 30, 4, 5, "access-jti-0001",
                NOW.minusSeconds(10), NOW.plusSeconds(290));
    }

    private static SessionSecurityProjection snapshot(
            long tokenVersion,
            long sessionVersion,
            String status,
            Instant idleExpiresAt,
            Instant absoluteExpiresAt) {
        return new SessionSecurityProjection(
                10, 20, 30, tokenVersion, sessionVersion,
                SessionProjectionStatus.valueOf(status),
                idleExpiresAt, absoluteExpiresAt);
    }
}
