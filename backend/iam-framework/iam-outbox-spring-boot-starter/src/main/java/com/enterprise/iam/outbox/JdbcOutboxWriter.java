package com.enterprise.iam.outbox;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Timestamp;
import java.util.Objects;

/** Inserts the outbox row only while already participating in a write transaction. */
public final class JdbcOutboxWriter implements OutboxWriter {

    static final String INSERT_SQL = """
            INSERT INTO sys_outbox_event (
                id, event_id, tenant_id, aggregate_type, aggregate_id,
                aggregate_version, event_type, schema_version, exchange_name,
                routing_key, payload, event_status, retry_count, next_retry_at,
                created_at, version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'iam.local', ?, ?, 'PENDING', 0, ?, ?, 0)
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcOutboxWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(
                jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    public void append(OutboxEventToAppend event) {
        Objects.requireNonNull(event, "event must not be null");
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "outbox append requires an already-active business transaction");
        }
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("outbox append rejects a read-only transaction");
        }
        Timestamp occurredAt = Timestamp.from(event.occurredAt());
        int updated = jdbcTemplate.update(
                INSERT_SQL,
                event.id(),
                event.eventId(),
                event.tenantId(),
                event.aggregateType(),
                event.aggregateId(),
                event.aggregateVersion(),
                event.eventType(),
                event.schemaVersion(),
                event.eventType(),
                event.payload(),
                occurredAt,
                occurredAt);
        if (updated != 1) {
            throw new IllegalStateException("outbox append did not insert exactly one row");
        }
    }
}
