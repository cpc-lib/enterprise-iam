package com.enterprise.iam.common.mq;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * AMQP publisher for {@link DomainEvent} envelopes. The payload map is
 * serialized with the shared JSON converter; routing metadata travels in
 * explicit {@code iam-*} headers and every message is delivered persistently.
 */
public final class AmqpDomainEventGateway implements DomainEventGateway {

    static final String HEADER_EVENT_TYPE = "iam-event-type";
    static final String HEADER_SCHEMA_VERSION = "iam-schema-version";
    static final String HEADER_EVENT_ID = "iam-event-id";
    static final String HEADER_TENANT_ID = "iam-tenant-id";
    static final String HEADER_AGGREGATE_TYPE = "iam-aggregate-type";
    static final String HEADER_AGGREGATE_ID = "iam-aggregate-id";

    private final RabbitTemplate rabbitTemplate;
    private final MessageConverter messageConverter;

    public AmqpDomainEventGateway(RabbitTemplate rabbitTemplate, MessageConverter messageConverter) {
        this.rabbitTemplate = Objects.requireNonNull(rabbitTemplate, "rabbitTemplate must not be null");
        this.messageConverter = Objects.requireNonNull(messageConverter, "messageConverter must not be null");
    }

    @Override
    public void publish(String exchange, String routingKey, DomainEvent event) {
        Objects.requireNonNull(exchange, "exchange must not be null");
        Objects.requireNonNull(routingKey, "routingKey must not be null");
        Objects.requireNonNull(event, "event must not be null");

        MessageProperties properties = new MessageProperties();
        Message message = messageConverter.toMessage(event.payload(), properties);
        MessageProperties headers = message.getMessageProperties();
        headers.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        headers.setContentEncoding(StandardCharsets.UTF_8.name());
        headers.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        headers.setHeader(HEADER_EVENT_TYPE, event.eventType());
        headers.setHeader(HEADER_SCHEMA_VERSION, event.schemaVersion());
        headers.setHeader(HEADER_EVENT_ID, event.eventId());
        headers.setHeader(HEADER_TENANT_ID, event.tenantId());
        headers.setHeader(HEADER_AGGREGATE_TYPE, event.aggregateType());
        headers.setHeader(HEADER_AGGREGATE_ID, event.aggregateId());
        // Consumers perform their own typed conversion; never trust class hints.
        headers.getHeaders().remove("__TypeId__");
        headers.getHeaders().remove("__ContentTypeId__");
        headers.getHeaders().remove("__KeyTypeId__");

        rabbitTemplate.send(exchange, routingKey, message);
    }
}
