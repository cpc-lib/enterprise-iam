package com.enterprise.iam.common.mq;

/**
 * Publishes domain events to the given broker exchange/routing key.
 * Implementations must be safe to invoke from an outbox relay handler and
 * must not open surrounding business transactions.
 */
public interface DomainEventGateway {

    void publish(String exchange, String routingKey, DomainEvent event);
}
