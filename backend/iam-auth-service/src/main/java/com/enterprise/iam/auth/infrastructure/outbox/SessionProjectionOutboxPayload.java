package com.enterprise.iam.auth.infrastructure.outbox;

import com.enterprise.iam.common.security.session.SessionProjectionStatus;
import com.enterprise.iam.common.security.session.SessionSecurityProjection;

import java.time.Instant;
import java.util.Objects;

record SessionProjectionOutboxPayload(
        int schemaVersion,
        long tenantId,
        long subjectId,
        long sessionId,
        long tokenVersion,
        long sessionVersion,
        String status,
        long idleExpiresAtEpochMs,
        long absoluteExpiresAtEpochMs) {

    static final int SCHEMA_VERSION = 1;

    SessionProjectionOutboxPayload {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported session projection schemaVersion");
        }
        requirePositive(tenantId, "tenantId");
        requirePositive(subjectId, "subjectId");
        requirePositive(sessionId, "sessionId");
        requirePositive(tokenVersion, "tokenVersion");
        requirePositive(sessionVersion, "sessionVersion");
        Objects.requireNonNull(status, "status must not be null");
        SessionProjectionStatus.valueOf(status);
        requirePositive(idleExpiresAtEpochMs, "idleExpiresAtEpochMs");
        requirePositive(absoluteExpiresAtEpochMs, "absoluteExpiresAtEpochMs");
        if (idleExpiresAtEpochMs > absoluteExpiresAtEpochMs) {
            throw new IllegalArgumentException(
                    "idleExpiresAtEpochMs must not exceed absoluteExpiresAtEpochMs");
        }
    }

    static SessionProjectionOutboxPayload from(SessionSecurityProjection projection) {
        Objects.requireNonNull(projection, "projection must not be null");
        return new SessionProjectionOutboxPayload(
                SCHEMA_VERSION,
                projection.tenantId(),
                projection.subjectId(),
                projection.sessionId(),
                projection.tokenVersion(),
                projection.sessionVersion(),
                projection.status().name(),
                projection.idleExpiresAt().toEpochMilli(),
                projection.absoluteExpiresAt().toEpochMilli());
    }

    SessionSecurityProjection toProjection() {
        return new SessionSecurityProjection(
                tenantId,
                subjectId,
                sessionId,
                tokenVersion,
                sessionVersion,
                SessionProjectionStatus.valueOf(status),
                Instant.ofEpochMilli(idleExpiresAtEpochMs),
                Instant.ofEpochMilli(absoluteExpiresAtEpochMs));
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
