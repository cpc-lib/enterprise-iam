package com.enterprise.iam.auth.infrastructure.outbox;

import com.enterprise.iam.auth.application.port.out.SessionSecurityProjectionPublisher;
import com.enterprise.iam.common.security.session.SessionSecurityProjection;
import com.enterprise.iam.outbox.OutboxEvent;
import com.enterprise.iam.outbox.OutboxEventHandler;
import com.enterprise.iam.outbox.OutboxNonRetryableException;

import java.util.Objects;

/** Idempotent handler backed by the monotonic Redis projection publisher. */
public final class SessionProjectionOutboxEventHandler implements OutboxEventHandler {

    private final SessionProjectionEventCodec codec;
    private final SessionSecurityProjectionPublisher publisher;

    public SessionProjectionOutboxEventHandler(
            SessionProjectionEventCodec codec,
            SessionSecurityProjectionPublisher publisher) {
        this.codec = Objects.requireNonNull(codec, "codec must not be null");
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
    }

    @Override
    public String eventType() {
        return JdbcSessionProjectionOutboxAppender.EVENT_TYPE;
    }

    @Override
    public int schemaVersion() {
        return SessionProjectionOutboxPayload.SCHEMA_VERSION;
    }

    @Override
    public void handle(OutboxEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        SessionSecurityProjection projection;
        try {
            projection = codec.decode(event.payload());
        } catch (SessionProjectionEventFormatException exception) {
            throw new OutboxNonRetryableException("INVALID_EVENT_PAYLOAD", exception);
        }
        if (!JdbcSessionProjectionOutboxAppender.AGGREGATE_TYPE.equals(event.aggregateType())
                || event.tenantId() != projection.tenantId()
                || event.aggregateId() != projection.sessionId()
                || event.aggregateVersion() != projection.sessionVersion()) {
            throw new OutboxNonRetryableException("INVALID_EVENT_METADATA");
        }
        publisher.publish(projection);
    }
}
