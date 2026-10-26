package com.enterprise.iam.common.core.idempotency;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory idempotency store for single-node deployments and tests.
 * Production deployments must use a JDBC/Redis implementation with atomic
 * claim semantics.
 */
public final class InMemoryIdempotencyStore implements IdempotencyStore {

    private final Map<String, IdempotencyRecord> store = new ConcurrentHashMap<>();

    @Override
    public Optional<IdempotencyRecord> findAndClaim(IdempotencyRecord record) {
        String key = record.tenantId() + ":" + record.idempotencyKey();
        IdempotencyRecord existing = store.putIfAbsent(key, record);
        return Optional.ofNullable(existing);
    }

    @Override
    public Optional<IdempotencyRecord> find(long tenantId, String idempotencyKey) {
        String key = tenantId + ":" + idempotencyKey;
        return Optional.ofNullable(store.get(key));
    }
}
