package com.enterprise.iam.auth.application.service;

import com.enterprise.iam.auth.application.service.RefreshRotationPolicy.Decision;
import com.enterprise.iam.auth.application.service.RefreshRotationPolicy.RefreshTokenState;
import com.enterprise.iam.auth.application.service.RefreshRotationPolicy.RefreshTokenStatus;
import com.enterprise.iam.auth.application.service.RefreshRotationPolicy.SessionState;
import com.enterprise.iam.auth.application.service.RefreshRotationPolicy.SessionStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefreshRotationPolicyTest {

    private static final Instant NOW = Instant.parse("2026-08-12T12:00:00Z");
    private static final Instant TOKEN_EXPIRY = NOW.plusSeconds(3_600);
    private static final Instant IDLE_EXPIRY = NOW.plusSeconds(1_800);
    private static final Instant ABSOLUTE_EXPIRY = NOW.plusSeconds(86_400);

    private final RefreshRotationPolicy policy = new RefreshRotationPolicy();

    @Test
    void freshActiveTokenOnActiveSessionRotates() {
        assertThat(policy.decide(
                token(RefreshTokenStatus.ACTIVE, TOKEN_EXPIRY),
                session(SessionStatus.ACTIVE),
                NOW)).isEqualTo(Decision.ROTATE);
    }

    @Test
    void replayedRotatedTokenSignalsReuse() {
        assertThat(policy.decide(
                token(RefreshTokenStatus.ROTATED, TOKEN_EXPIRY),
                session(SessionStatus.ACTIVE),
                NOW)).isEqualTo(Decision.REUSE_DETECTED);
    }

    @Test
    void alreadyMarkedReusedTokenSignalsReuseAgain() {
        assertThat(policy.decide(
                token(RefreshTokenStatus.REUSED, TOKEN_EXPIRY),
                session(SessionStatus.ACTIVE),
                NOW)).isEqualTo(Decision.REUSE_DETECTED);
    }

    @Test
    void revokedTokenIsRejectedWithoutFamilyRevocation() {
        assertThat(policy.decide(
                token(RefreshTokenStatus.REVOKED, TOKEN_EXPIRY),
                session(SessionStatus.ACTIVE),
                NOW)).isEqualTo(Decision.REJECT);
    }

    @Test
    void expiredMarkedTokenIsRejectedWithoutFamilyRevocation() {
        assertThat(policy.decide(
                token(RefreshTokenStatus.EXPIRED, TOKEN_EXPIRY),
                session(SessionStatus.ACTIVE),
                NOW)).isEqualTo(Decision.REJECT);
    }

    @Test
    void activeTokenPastItsOwnExpiryIsRejectedNaturally() {
        assertThat(policy.decide(
                token(RefreshTokenStatus.ACTIVE, NOW),
                session(SessionStatus.ACTIVE),
                NOW)).isEqualTo(Decision.REJECT);
        assertThat(policy.decide(
                token(RefreshTokenStatus.ACTIVE, NOW.minusSeconds(1)),
                session(SessionStatus.ACTIVE),
                NOW)).isEqualTo(Decision.REJECT);
    }

    @Test
    void activeTokenExpiringOneNanosecondLaterStillRotates() {
        assertThat(policy.decide(
                token(RefreshTokenStatus.ACTIVE, NOW.plusNanos(1)),
                session(SessionStatus.ACTIVE),
                NOW)).isEqualTo(Decision.ROTATE);
    }

    @Test
    void liveTokenOnRevokedSessionSignalsReuse() {
        assertThat(policy.decide(
                token(RefreshTokenStatus.ACTIVE, TOKEN_EXPIRY),
                session(SessionStatus.REVOKED),
                NOW)).isEqualTo(Decision.REUSE_DETECTED);
    }

    @Test
    void liveTokenOnExpiredSessionSignalsReuse() {
        assertThat(policy.decide(
                token(RefreshTokenStatus.ACTIVE, TOKEN_EXPIRY),
                session(SessionStatus.EXPIRED),
                NOW)).isEqualTo(Decision.REUSE_DETECTED);
    }

    @Test
    void idleExpiredSessionIsRejectedNaturally() {
        assertThat(policy.decide(
                token(RefreshTokenStatus.ACTIVE, TOKEN_EXPIRY),
                session(SessionStatus.ACTIVE, NOW, ABSOLUTE_EXPIRY),
                NOW)).isEqualTo(Decision.REJECT);
    }

    @Test
    void absolutelyExpiredSessionIsRejectedNaturally() {
        assertThat(policy.decide(
                token(RefreshTokenStatus.ACTIVE, TOKEN_EXPIRY),
                session(SessionStatus.ACTIVE, IDLE_EXPIRY, NOW),
                NOW)).isEqualTo(Decision.REJECT);
    }

    @Test
    void nullInputsAreRejected() {
        assertThatThrownBy(() -> policy.decide(null, session(SessionStatus.ACTIVE), NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> policy.decide(
                token(RefreshTokenStatus.ACTIVE, TOKEN_EXPIRY), null, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> policy.decide(
                token(RefreshTokenStatus.ACTIVE, TOKEN_EXPIRY),
                session(SessionStatus.ACTIVE),
                null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void stateRecordsRejectNulls() {
        assertThatThrownBy(() -> new RefreshTokenState(null, TOKEN_EXPIRY))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RefreshTokenState(RefreshTokenStatus.ACTIVE, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SessionState(null, IDLE_EXPIRY, ABSOLUTE_EXPIRY))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SessionState(SessionStatus.ACTIVE, null, ABSOLUTE_EXPIRY))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SessionState(SessionStatus.ACTIVE, IDLE_EXPIRY, null))
                .isInstanceOf(NullPointerException.class);
    }

    private static RefreshTokenState token(RefreshTokenStatus status, Instant expireAt) {
        return new RefreshTokenState(status, expireAt);
    }

    private static SessionState session(SessionStatus status) {
        return session(status, IDLE_EXPIRY, ABSOLUTE_EXPIRY);
    }

    private static SessionState session(
            SessionStatus status, Instant idleExpireAt, Instant absoluteExpireAt) {
        return new SessionState(status, idleExpireAt, absoluteExpireAt);
    }
}
