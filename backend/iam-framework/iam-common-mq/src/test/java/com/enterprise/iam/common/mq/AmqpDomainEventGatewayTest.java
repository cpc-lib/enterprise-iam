package com.enterprise.iam.common.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AmqpDomainEventGatewayTest {

    private RabbitTemplate rabbitTemplate;
    private AmqpDomainEventGateway gateway;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        gateway = new AmqpDomainEventGateway(
                rabbitTemplate, new Jackson2JsonMessageConverter(objectMapper));
    }

    @Test
    void publishesToRequestedExchangeAndRoutingKeyWithMetadataHeaders() throws Exception {
        Instant occurredAt = Instant.parse("2026-09-24T10:15:30Z");
        DomainEvent event = new DomainEvent(
                9001L,
                "share.acl.granted",
                1,
                7L,
                "RESOURCE_SHARE",
                5001L,
                occurredAt,
                Map.of("shareId", 5001L, "tenantId", 7L, "epoch", 1));

        gateway.publish("iam.domain.events", "share.acl.granted", event);

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate, times(1))
                .send(org.mockito.ArgumentMatchers.eq("iam.domain.events"),
                        org.mockito.ArgumentMatchers.eq("share.acl.granted"),
                        messageCaptor.capture());
        Message message = messageCaptor.getValue();

        assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(message.getMessageProperties().getDeliveryMode())
                .isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(message.getMessageProperties().getHeaders())
                .containsEntry("iam-event-type", "share.acl.granted")
                .containsEntry("iam-schema-version", 1)
                .containsEntry("iam-event-id", 9001L)
                .containsEntry("iam-tenant-id", 7L)
                .containsEntry("iam-aggregate-type", "RESOURCE_SHARE")
                .containsEntry("iam-aggregate-id", 5001L)
                .doesNotContainKey("__TypeId__");

        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = objectMapper.readValue(body, Map.class);
        assertThat(parsed).containsEntry("shareId", 5001);
        assertThat(parsed).containsEntry("tenantId", 7);
        assertThat(parsed).containsEntry("epoch", 1);
    }

    @Test
    void rejectsNullArguments() {
        DomainEvent event = new DomainEvent(
                1L, "share.acl.revoked", 1, 1L, "RESOURCE_SHARE", 1L,
                Instant.parse("2026-09-24T10:15:30Z"), Map.of("epoch", 2));
        org.junit.jupiter.api.Assertions.assertThrows(
                NullPointerException.class,
                () -> gateway.publish(null, "rk", event));
        org.junit.jupiter.api.Assertions.assertThrows(
                NullPointerException.class,
                () -> gateway.publish("ex", null, event));
        org.junit.jupiter.api.Assertions.assertThrows(
                NullPointerException.class,
                () -> gateway.publish("ex", "rk", null));
    }
}
