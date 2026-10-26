package com.enterprise.iam.auth.application.port.out;

import java.time.Instant;

/** Appends durable login audit facts to the caller's active transaction. */
public interface LoginAuditOutboxAppender {

    void appendLoginSucceeded(
            long eventId,
            long tenantId,
            long userId,
            long sessionId,
            String requestId,
            Instant occurredAt);

    void appendOrphanReconciled(
            long eventId,
            long tenantId,
            long userId,
            long sessionId,
            long sessionVersion,
            String requestId,
            Instant occurredAt);

    void appendPasswordChanged(
            long eventId,
            long tenantId,
            long userId,
            long sessionId,
            long sessionVersion,
            String requestId,
            Instant occurredAt);
}
