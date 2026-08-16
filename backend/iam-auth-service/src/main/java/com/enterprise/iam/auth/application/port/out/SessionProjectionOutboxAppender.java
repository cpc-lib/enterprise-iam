package com.enterprise.iam.auth.application.port.out;

import com.enterprise.iam.common.security.session.SessionSecurityProjection;

import java.time.Instant;

/**
 * Appends a projection event inside the caller's existing session write transaction.
 */
@FunctionalInterface
public interface SessionProjectionOutboxAppender {

    void append(long eventId, SessionSecurityProjection projection, Instant occurredAt);
}
