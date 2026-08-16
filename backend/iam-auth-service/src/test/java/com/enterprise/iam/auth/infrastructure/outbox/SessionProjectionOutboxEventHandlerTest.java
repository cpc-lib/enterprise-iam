package com.enterprise.iam.auth.infrastructure.outbox;

import com.enterprise.iam.auth.application.port.out.ProjectionWriteResult;
import com.enterprise.iam.common.security.session.SessionSecurityProjection;
import com.enterprise.iam.outbox.OutboxEvent;
import com.enterprise.iam.outbox.OutboxNonRetryableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionProjectionOutboxEventHandlerTest {

    private final SessionProjectionEventCodec codec =
            new SessionProjectionEventCodec(new ObjectMapper());

    @Test
    void verifiesMetadataAndDelegatesToMonotonicIdempotentPublisher() {
        AtomicReference<SessionSecurityProjection> published = new AtomicReference<>();
        SessionProjectionOutboxEventHandler handler =
                new SessionProjectionOutboxEventHandler(codec, projection -> {
                    published.set(projection);
                    return ProjectionWriteResult.STALE_IGNORED;
                });

        assertThatCode(() -> handler.handle(event(10, 30, 5, validPayload())))
                .doesNotThrowAnyException();
        assertThat(published.get()).isEqualTo(SessionProjectionEventCodecTest.projection());
    }

    @Test
    void classifiesBadPayloadAndMetadataAsNonRetryable() {
        SessionProjectionOutboxEventHandler handler =
                new SessionProjectionOutboxEventHandler(
                        codec, projection -> ProjectionWriteResult.APPLIED);

        assertThatThrownBy(() -> handler.handle(event(10, 30, 5, "{}")))
                .isInstanceOfSatisfying(OutboxNonRetryableException.class,
                        failure -> assertThat(failure.failureCode())
                                .isEqualTo("INVALID_EVENT_PAYLOAD"));
        assertThatThrownBy(() -> handler.handle(event(11, 30, 5, validPayload())))
                .isInstanceOfSatisfying(OutboxNonRetryableException.class,
                        failure -> assertThat(failure.failureCode())
                                .isEqualTo("INVALID_EVENT_METADATA"));
        assertThatThrownBy(() -> handler.handle(event(10, 30, 6, validPayload())))
                .isInstanceOf(OutboxNonRetryableException.class);
    }

    private String validPayload() {
        return codec.encode(SessionProjectionEventCodecTest.projection());
    }

    private static OutboxEvent event(
            long tenantId,
            long aggregateId,
            long aggregateVersion,
            String payload) {
        return new OutboxEvent(
                101,
                101,
                tenantId,
                JdbcSessionProjectionOutboxAppender.AGGREGATE_TYPE,
                aggregateId,
                aggregateVersion,
                JdbcSessionProjectionOutboxAppender.EVENT_TYPE,
                1,
                payload,
                0,
                Instant.parse("2026-08-12T12:00:00Z"));
    }
}
