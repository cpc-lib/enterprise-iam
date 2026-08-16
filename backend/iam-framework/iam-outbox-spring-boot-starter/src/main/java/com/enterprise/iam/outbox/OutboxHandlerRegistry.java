package com.enterprise.iam.outbox;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable exact event-type and schema-version dispatch registry. */
public final class OutboxHandlerRegistry {

    private final Map<HandlerKey, OutboxEventHandler> handlers;

    public OutboxHandlerRegistry(Collection<OutboxEventHandler> handlers) {
        Objects.requireNonNull(handlers, "handlers must not be null");
        Map<HandlerKey, OutboxEventHandler> configured = new HashMap<>();
        for (OutboxEventHandler handler : handlers) {
            Objects.requireNonNull(handler, "handler must not be null");
            String eventType = OutboxEvent.requireType(handler.eventType(), "eventType");
            if (handler.schemaVersion() <= 0) {
                throw new IllegalArgumentException("handler schemaVersion must be positive");
            }
            HandlerKey key = new HandlerKey(eventType, handler.schemaVersion());
            if (configured.putIfAbsent(key, handler) != null) {
                throw new IllegalStateException("duplicate outbox handler: " + key);
            }
        }
        this.handlers = Map.copyOf(configured);
    }

    public Optional<OutboxEventHandler> find(String eventType, int schemaVersion) {
        return Optional.ofNullable(handlers.get(new HandlerKey(eventType, schemaVersion)));
    }

    public boolean isEmpty() {
        return handlers.isEmpty();
    }

    private record HandlerKey(String eventType, int schemaVersion) {
    }
}
