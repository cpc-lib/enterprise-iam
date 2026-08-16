package com.enterprise.iam.auth.application.service;

import java.time.Instant;
import java.util.Objects;

/**
 * Pure RFC 9700 rotation decision. A replayed or already-rotated token is a
 * theft signal and revokes the whole token family; natural expiry and idle
 * sessions are rejected without family revocation.
 */
public final class RefreshRotationPolicy {

    public enum Decision {ROTATE, REUSE_DETECTED, REJECT}

    public enum RefreshTokenStatus {ACTIVE, ROTATED, REVOKED, REUSED, EXPIRED}

    public enum SessionStatus {ACTIVE, REVOKED, EXPIRED}

    public record RefreshTokenState(RefreshTokenStatus status, Instant expireAt) {
        public RefreshTokenState {
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(expireAt, "expireAt must not be null");
        }
    }

    public record SessionState(
            SessionStatus status,
            Instant idleExpireAt,
            Instant absoluteExpireAt) {
        public SessionState {
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(idleExpireAt, "idleExpireAt must not be null");
            Objects.requireNonNull(absoluteExpireAt, "absoluteExpireAt must not be null");
        }
    }

    public Decision decide(RefreshTokenState token, SessionState session, Instant now) {
        Objects.requireNonNull(token, "token must not be null");
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(now, "now must not be null");

        if (token.status() == RefreshTokenStatus.ROTATED
                || token.status() == RefreshTokenStatus.REUSED) {
            return Decision.REUSE_DETECTED;
        }
        if (token.status() == RefreshTokenStatus.REVOKED
                || token.status() == RefreshTokenStatus.EXPIRED) {
            return Decision.REJECT;
        }
        if (!token.expireAt().isAfter(now)) {
            return Decision.REJECT;
        }
        if (session.status() != SessionStatus.ACTIVE) {
            return Decision.REUSE_DETECTED;
        }
        if (!session.idleExpireAt().isAfter(now)
                || !session.absoluteExpireAt().isAfter(now)) {
            return Decision.REJECT;
        }
        return Decision.ROTATE;
    }
}
