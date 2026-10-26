package com.enterprise.iam.outbox;

import com.enterprise.iam.outbox.mapper.OutboxPersistenceMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;

/** Inserts the outbox row only while already participating in a write transaction. */
public final class JdbcOutboxWriter implements OutboxWriter {

    private final OutboxPersistenceMapper mapper;

    public JdbcOutboxWriter(OutboxPersistenceMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
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
        int updated = mapper.insertOutboxEvent(event);
        if (updated != 1) {
            throw new IllegalStateException("outbox append did not insert exactly one row");
        }
    }
}
