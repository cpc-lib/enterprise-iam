package com.enterprise.iam.auth.infrastructure.outbox;

import com.enterprise.iam.auth.application.port.out.SessionProjectionOutboxAppender;
import com.enterprise.iam.common.security.session.SessionSecurityProjection;
import com.enterprise.iam.outbox.OutboxEventToAppend;
import com.enterprise.iam.outbox.OutboxWriter;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;

/** Delegates to the transaction-enforcing JDBC writer; never opens a transaction. */
public final class JdbcSessionProjectionOutboxAppender
        implements SessionProjectionOutboxAppender {

    public static final String EVENT_TYPE = "iam.auth.session-security-projection";
    public static final String AGGREGATE_TYPE = "LOGIN_SESSION";

    private final Supplier<OutboxWriter> writer;
    private final SessionProjectionEventCodec codec;

    public JdbcSessionProjectionOutboxAppender(
            OutboxWriter writer,
            SessionProjectionEventCodec codec) {
        this(requireWriter(writer), codec);
    }

    JdbcSessionProjectionOutboxAppender(
            Supplier<OutboxWriter> writer,
            SessionProjectionEventCodec codec) {
        this.writer = Objects.requireNonNull(writer, "writer must not be null");
        this.codec = Objects.requireNonNull(codec, "codec must not be null");
    }

    @Override
    public void append(
            long eventId,
            SessionSecurityProjection projection,
            Instant occurredAt) {
        Objects.requireNonNull(projection, "projection must not be null");
        writer.get().append(new OutboxEventToAppend(
                eventId,
                eventId,
                projection.tenantId(),
                AGGREGATE_TYPE,
                projection.sessionId(),
                projection.sessionVersion(),
                EVENT_TYPE,
                SessionProjectionOutboxPayload.SCHEMA_VERSION,
                codec.encode(projection),
                occurredAt));
    }

    private static Supplier<OutboxWriter> requireWriter(OutboxWriter writer) {
        OutboxWriter required = Objects.requireNonNull(writer, "writer must not be null");
        return () -> required;
    }
}
