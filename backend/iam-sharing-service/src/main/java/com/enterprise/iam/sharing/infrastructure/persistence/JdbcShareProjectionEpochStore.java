package com.enterprise.iam.sharing.infrastructure.persistence;

import com.enterprise.iam.sharing.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.sharing.application.port.out.ShareProjectionEpochStore;
import com.enterprise.iam.sharing.infrastructure.persistence.mapper.SharingPersistenceMapper;

import java.time.Instant;
import java.util.Objects;

/**
 * Upserts the per-(tenant, resource) projection fence. The first call for a
 * resource (whether from CreateShareUseCase or RevokeShareUseCase) establishes
 * epoch 1; every later call increments the fence within the caller's
 * transaction. Both granted and revoked events carry the next monotonic
 * epoch so the authorization-service projection fence
 * ({@code iam_share_security_epoch}) mirrors exactly.
 */
public final class JdbcShareProjectionEpochStore implements ShareProjectionEpochStore {

    static final long INITIAL_EPOCH = 1L;

    private final SharingPersistenceMapper mapper;
    private final PositiveIdGenerator idGenerator;

    public JdbcShareProjectionEpochStore(
            SharingPersistenceMapper mapper,
            PositiveIdGenerator idGenerator) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator must not be null");
    }

    @Override
    public long nextEpoch(long tenantId, long resourceId) {
        if (tenantId <= 0 || resourceId <= 0) {
            throw new IllegalArgumentException("tenantId and resourceId must be positive");
        }
        Instant now = Instant.now();
        int updated = mapper.upsertShareProjectionEpoch(
                idGenerator.nextId(),
                tenantId,
                resourceId,
                INITIAL_EPOCH,
                now);
        if (updated != 1 && updated != 2) {
            throw new IllegalStateException("epoch upsert did not affect exactly one row");
        }
        Long epoch = mapper.findShareProjectionEpoch(tenantId, resourceId);
        if (epoch == null || epoch < INITIAL_EPOCH) {
            throw new IllegalStateException("projection epoch could not be established");
        }
        return epoch;
    }
}
