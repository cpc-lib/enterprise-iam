package com.enterprise.iam.sharing.infrastructure.outbox;

import com.enterprise.iam.common.mq.DomainEvent;
import com.enterprise.iam.common.mq.DomainEventGateway;
import com.enterprise.iam.outbox.OutboxEvent;
import com.enterprise.iam.outbox.OutboxNonRetryableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShareAclOutboxEventHandlerTest {

    private static final Instant CREATED = Instant.parse("2026-09-24T09:31:00Z");

    private ObjectMapper objectMapper;
    private RecordingGateway gateway;
    private ShareAclGrantedOutboxEventHandler grantedHandler;
    private ShareAclRevokedOutboxEventHandler revokedHandler;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        ShareAclEventCodec codec = new ShareAclEventCodec(objectMapper);
        gateway = new RecordingGateway();
        grantedHandler = new ShareAclGrantedOutboxEventHandler(
                codec, gateway, "iam.domain.events");
        revokedHandler = new ShareAclRevokedOutboxEventHandler(
                codec, gateway, "iam.domain.events");
    }

    @Test
    void grantedHandlerPublishesMappedDomainEvent() {
        String payload = """
                {"shareId":100,"tenantId":7,"resourceId":9,"resourceInstanceKey":"invoice-42",
                 "targetType":"USER","targetId":11,"operationIds":[21,22],
                 "startTime":"2026-09-24T08:59:00Z","expireTime":"2026-09-24T10:00:00Z",
                 "canReshare":false,"epoch":1}
                """;
        grantedHandler.handle(event("share.acl.granted", 1, 100L, 1L, payload));

        assertThat(gateway.published).hasSize(1);
        RecordingGateway.Call call = gateway.published.get(0);
        assertThat(call.exchange).isEqualTo("iam.domain.events");
        assertThat(call.routingKey).isEqualTo("share.acl.granted");
        DomainEvent domainEvent = call.event;
        assertThat(domainEvent.eventId()).isEqualTo(7777L);
        assertThat(domainEvent.eventType()).isEqualTo("share.acl.granted");
        assertThat(domainEvent.schemaVersion()).isEqualTo(1);
        assertThat(domainEvent.tenantId()).isEqualTo(7L);
        assertThat(domainEvent.aggregateType()).isEqualTo("RESOURCE_SHARE");
        assertThat(domainEvent.aggregateId()).isEqualTo(100L);
        assertThat(domainEvent.occurredAt()).isEqualTo(CREATED);
        assertThat(domainEvent.payload()).containsEntry("shareId", 100);
    }

    @Test
    void grantedHandlerRelaysReGrantEventWithAdvancingEpoch() {
        // After a revoke advanced the fence to 2, a re-grant carries epoch 3.
        // The granted handler must accept any positive epoch as long as the
        // outbox aggregate version matches the payload epoch.
        String payload = """
                {"shareId":100,"tenantId":7,"resourceId":9,"resourceInstanceKey":"invoice-42",
                 "targetType":"USER","targetId":11,"operationIds":[21],
                 "startTime":"2026-09-25T08:59:00Z","expireTime":"2026-09-25T10:00:00Z",
                 "canReshare":false,"epoch":3}
                """;
        grantedHandler.handle(event("share.acl.granted", 1, 100L, 3L, payload));

        assertThat(gateway.published).hasSize(1);
        assertThat(gateway.published.get(0).event.payload()).containsEntry("epoch", 3);
    }

    @Test
    void grantedHandlerDeadLettersWhenAggregateVersionLagsPayloadEpoch() {
        // payload epoch 3 but the outbox row still carries aggregateVersion 1:
        // metadata is inconsistent and must be dead-lettered, not republished.
        String payload = """
                {"shareId":100,"tenantId":7,"resourceId":9,"resourceInstanceKey":"k",
                 "targetType":"USER","targetId":11,"operationIds":[21],
                 "startTime":"2026-09-25T08:59:00Z","expireTime":"2026-09-25T10:00:00Z",
                 "canReshare":false,"epoch":3}
                """;
        assertThatThrownBy(() -> grantedHandler.handle(
                        event("share.acl.granted", 1, 100L, 1L, payload)))
                .isInstanceOf(OutboxNonRetryableException.class);
        assertThat(gateway.published).isEmpty();
    }

    @Test
    void grantedHandlerDeadLettersNonPositiveEpoch() {
        // The outbox row itself is well-formed (aggregateVersion 1) but the
        // payload carries a poisoned epoch 0; decode must dead-letter it.
        String payload = """
                {"shareId":100,"tenantId":7,"resourceId":9,"resourceInstanceKey":"k",
                 "targetType":"USER","targetId":11,"operationIds":[21],
                 "startTime":"2026-09-25T08:59:00Z","expireTime":"2026-09-25T10:00:00Z",
                 "canReshare":false,"epoch":0}
                """;
        assertThatThrownBy(() -> grantedHandler.handle(
                        event("share.acl.granted", 1, 100L, 1L, payload)))
                .isInstanceOf(OutboxNonRetryableException.class);
        assertThat(gateway.published).isEmpty();
    }

    @Test
    void revokedHandlerPublishesMappedDomainEvent() {
        String payload = """
                {"shareId":100,"tenantId":7,"resourceId":9,"resourceInstanceKey":"invoice-42",
                 "targetType":"TEAM","targetId":11,"operationIds":[21],"epoch":2}
                """;
        revokedHandler.handle(event("share.acl.revoked", 1, 100L, 2L, payload));

        RecordingGateway.Call call = gateway.published.get(0);
        assertThat(call.routingKey).isEqualTo("share.acl.revoked");
        assertThat(call.event.payload()).containsEntry("epoch", 2);
    }

    @Test
    void duplicateDeliveryRepublishesIdempotently() {
        String payload = """
                {"shareId":100,"tenantId":7,"resourceId":9,"resourceInstanceKey":"invoice-42",
                 "targetType":"USER","targetId":11,"operationIds":[21],
                 "startTime":"2026-09-24T08:59:00Z","expireTime":"2026-09-24T10:00:00Z",
                 "canReshare":true,"epoch":1}
                """;
        OutboxEvent event = event("share.acl.granted", 1, 100L, 1L, payload);
        grantedHandler.handle(event);
        grantedHandler.handle(event);
        assertThat(gateway.published).hasSize(2);
        assertThat(gateway.published.get(0).event).isEqualTo(gateway.published.get(1).event);
    }

    @Test
    void malformedGrantedPayloadIsDeadLettered() {
        OutboxEvent event = event("share.acl.granted", 1, 100L, 1L,
                "{\"shareId\":100,\"tenantId\":7}");
        assertThatThrownBy(() -> grantedHandler.handle(event))
                .isInstanceOf(OutboxNonRetryableException.class);
        assertThat(gateway.published).isEmpty();
    }

    @Test
    void malformedJsonIsDeadLettered() {
        OutboxEvent event = event("share.acl.revoked", 1, 100L, 2L, "not-json");
        assertThatThrownBy(() -> revokedHandler.handle(event))
                .isInstanceOf(OutboxNonRetryableException.class);
    }

    @Test
    void metadataMismatchIsDeadLettered() {
        String payload = """
                {"shareId":100,"tenantId":8,"resourceId":9,"resourceInstanceKey":"k",
                 "targetType":"USER","targetId":11,"operationIds":[21],
                 "startTime":"2026-09-24T08:59:00Z","expireTime":"2026-09-24T10:00:00Z",
                 "canReshare":false,"epoch":1}
                """;
        assertThatThrownBy(() -> grantedHandler.handle(
                        event("share.acl.granted", 1, 100L, 1L, payload)))
                .isInstanceOf(OutboxNonRetryableException.class);
    }

    private static OutboxEvent event(
            String type, int schema, long aggregateId, long aggregateVersion, String payload) {
        return new OutboxEvent(
                1L, 7777L, 7L, "RESOURCE_SHARE", aggregateId, aggregateVersion,
                type, schema, payload, 0, CREATED);
    }

    static final class RecordingGateway implements DomainEventGateway {
        final List<Call> published = new ArrayList<>();

        @Override
        public void publish(String exchange, String routingKey, DomainEvent event) {
            published.add(new Call(exchange, routingKey, event));
        }

        record Call(String exchange, String routingKey, DomainEvent event) {
        }
    }
}
