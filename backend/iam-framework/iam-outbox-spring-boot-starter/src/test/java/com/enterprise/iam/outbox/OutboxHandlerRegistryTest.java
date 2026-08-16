package com.enterprise.iam.outbox;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxHandlerRegistryTest {

    @Test
    void dispatchesByExactTypeAndSchema() {
        OutboxEventHandler handler = handler("event.type", 2);
        OutboxHandlerRegistry registry = new OutboxHandlerRegistry(List.of(handler));

        assertThat(registry.find("event.type", 2)).contains(handler);
        assertThat(registry.find("event.type", 1)).isEmpty();
        assertThat(registry.find("event.other", 2)).isEmpty();
    }

    @Test
    void rejectsDuplicateRoutes() {
        assertThatThrownBy(() -> new OutboxHandlerRegistry(List.of(
                handler("event.type", 1), handler("event.type", 1))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate outbox handler");
    }

    private static OutboxEventHandler handler(String type, int schema) {
        return new OutboxEventHandler() {
            @Override
            public String eventType() {
                return type;
            }

            @Override
            public int schemaVersion() {
                return schema;
            }

            @Override
            public void handle(OutboxEvent event) {
            }
        };
    }
}
