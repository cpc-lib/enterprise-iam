package com.enterprise.iam.common.core.idempotency;

import java.util.Optional;

/**
 * Port for durable idempotency storage. Implementations must enforce the
 * uniqueness of (tenantId, idempotencyKey) atomically.
 */
public interface IdempotencyStore {

    /**
     * Attempts to claim the idempotency key. Returns the previously stored
     * record if the key already exists, otherwise empty.
     */
    Optional<IdempotencyRecord> findAndClaim(IdempotencyRecord record);

    Optional<IdempotencyRecord> find(long tenantId, String idempotencyKey);
}
