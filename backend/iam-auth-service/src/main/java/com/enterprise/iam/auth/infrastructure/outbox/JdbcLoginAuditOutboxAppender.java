package com.enterprise.iam.auth.infrastructure.outbox;

import com.enterprise.iam.auth.application.port.out.LoginAuditOutboxAppender;
import com.enterprise.iam.outbox.OutboxEventToAppend;
import com.enterprise.iam.outbox.OutboxWriter;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;

/** Writes security audit facts into the same physical transaction as session state. */
public final class JdbcLoginAuditOutboxAppender implements LoginAuditOutboxAppender {

    public static final String EVENT_TYPE_LOGIN_SUCCEEDED = "auth.login.succeeded";
    public static final String EVENT_TYPE_ORPHAN_RECONCILED = "auth.login.orphan-reconciled";
    public static final String EVENT_TYPE_PASSWORD_CHANGED = "auth.password.changed";
    public static final String AGGREGATE_TYPE = "LOGIN_SESSION";
    public static final String ORPHAN_REASON = "LOGIN_DELIVERY_UNCONFIRMED";
    public static final String PASSWORD_CHANGED_REASON = "PASSWORD_CHANGED";

    private final Supplier<OutboxWriter> writer;
    private final LoginAuditEventCodec codec;

    public JdbcLoginAuditOutboxAppender(OutboxWriter writer, LoginAuditEventCodec codec) {
        this(() -> Objects.requireNonNull(writer, "writer must not be null"), codec);
    }

    JdbcLoginAuditOutboxAppender(
            Supplier<OutboxWriter> writer,
            LoginAuditEventCodec codec) {
        this.writer = Objects.requireNonNull(writer, "writer must not be null");
        this.codec = Objects.requireNonNull(codec, "codec must not be null");
    }

    @Override
    public void appendLoginSucceeded(
            long eventId,
            long tenantId,
            long userId,
            long sessionId,
            String requestId,
            Instant occurredAt) {
        append(eventId, tenantId, userId, sessionId, 1, requestId, occurredAt,
                EVENT_TYPE_LOGIN_SUCCEEDED, null);
    }

    @Override
    public void appendOrphanReconciled(
            long eventId,
            long tenantId,
            long userId,
            long sessionId,
            long sessionVersion,
            String requestId,
            Instant occurredAt) {
        append(eventId, tenantId, userId, sessionId, sessionVersion, requestId, occurredAt,
                EVENT_TYPE_ORPHAN_RECONCILED, ORPHAN_REASON);
    }

    @Override
    public void appendPasswordChanged(
            long eventId,
            long tenantId,
            long userId,
            long sessionId,
            long sessionVersion,
            String requestId,
            Instant occurredAt) {
        append(eventId, tenantId, userId, sessionId, sessionVersion, requestId, occurredAt,
                EVENT_TYPE_PASSWORD_CHANGED, PASSWORD_CHANGED_REASON);
    }

    private void append(
            long eventId,
            long tenantId,
            long userId,
            long sessionId,
            long sessionVersion,
            String requestId,
            Instant occurredAt,
            String eventType,
            String reason) {
        LoginAuditOutboxPayload payload = new LoginAuditOutboxPayload(
                tenantId, userId, sessionId, requestId, occurredAt, reason);
        writer.get().append(new OutboxEventToAppend(
                eventId,
                eventId,
                tenantId,
                AGGREGATE_TYPE,
                sessionId,
                sessionVersion,
                eventType,
                LoginAuditOutboxPayload.SCHEMA_VERSION,
                codec.encode(payload),
                occurredAt));
    }
}
