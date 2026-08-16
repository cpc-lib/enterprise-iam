package com.enterprise.iam.gateway.security;

import com.enterprise.iam.common.security.access.VerifiedAccessToken;
import com.enterprise.iam.common.security.session.SessionProjectionStatus;
import com.enterprise.iam.common.security.session.SessionSecurityProjection;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** Exact session and security-version comparison; absent snapshots deny. */
public final class AuthoritativeReactiveSessionStateVerifier
        implements ReactiveSessionStateVerifier {

    private final ReactiveSessionSnapshotReader reader;
    private final Clock clock;

    public AuthoritativeReactiveSessionStateVerifier(
            ReactiveSessionSnapshotReader reader,
            Clock clock) {
        this.reader = Objects.requireNonNull(reader, "reader must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public Mono<SessionStateVerification> verify(VerifiedAccessToken token) {
        Objects.requireNonNull(token, "token must not be null");
        return Mono.defer(() -> reader.find(
                        token.tenantId(), token.subjectId(), token.sessionId()))
                .map(snapshot -> matches(token, snapshot, clock.instant())
                        ? SessionStateVerification.ACTIVE
                        : SessionStateVerification.INVALID)
                .defaultIfEmpty(SessionStateVerification.INVALID);
    }

    private static boolean matches(
            VerifiedAccessToken token,
            SessionSecurityProjection snapshot,
            Instant now) {
        return snapshot.tenantId() == token.tenantId()
                && snapshot.subjectId() == token.subjectId()
                && snapshot.sessionId() == token.sessionId()
                && snapshot.tokenVersion() == token.tokenVersion()
                && snapshot.sessionVersion() == token.sessionVersion()
                && SessionProjectionStatus.ACTIVE.equals(snapshot.status())
                && now.isBefore(snapshot.idleExpiresAt())
                && now.isBefore(snapshot.absoluteExpiresAt());
    }
}
