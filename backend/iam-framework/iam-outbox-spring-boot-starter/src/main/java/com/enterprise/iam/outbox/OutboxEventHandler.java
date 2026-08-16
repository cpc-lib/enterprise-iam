package com.enterprise.iam.outbox;

public interface OutboxEventHandler {

    String eventType();

    int schemaVersion();

    /** Must be idempotent because delivery is intentionally at least once. */
    void handle(OutboxEvent event);
}
