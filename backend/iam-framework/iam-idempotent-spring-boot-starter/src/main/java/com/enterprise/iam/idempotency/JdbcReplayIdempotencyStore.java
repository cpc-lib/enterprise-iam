package com.enterprise.iam.idempotency;

import com.enterprise.iam.idempotency.mapper.IdempotencyPersistenceMapper;
import com.enterprise.iam.idempotency.mapper.IdempotencyPersistenceMapper.RecordRow;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * JDBC-backed {@link ReplayIdempotencyStore} for multi-instance deployments
 * (SPEC 18, SPEC 47 D-12). {@code claim} runs in a row-locked transaction and
 * retries a first-insert race once, so concurrent callers serialise on
 * (tenant_id, idempotency_key); expired rows are taken over inside the same
 * lock. Behaviour mirrors
 * {@link InMemoryReplayIdempotencyStore}, including "same body in flight ->
 * 409" and "different body -> 409".
 */
public final class JdbcReplayIdempotencyStore implements ReplayIdempotencyStore {

    private static final String STATUS_IN_FLIGHT = "IN_FLIGHT";
    private static final String STATUS_COMMITTED = "COMMITTED";

    private final IdempotencyPersistenceMapper mapper;
    private final TransactionTemplate transactionTemplate;
    private final Duration ttl;

    public JdbcReplayIdempotencyStore(IdempotencyPersistenceMapper mapper,
                                      PlatformTransactionManager transactionManager,
                                      Duration ttl) {
        this.mapper = Objects.requireNonNull(mapper);
        this.transactionTemplate = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        this.ttl = Objects.requireNonNull(ttl);
    }

    @Override
    public ClaimOutcome claim(long tenantId, String key, String requestHash, Instant now) {
        try {
            return claimInTransaction(tenantId, key, requestHash, now);
        } catch (DuplicateKeyException | TransientDataAccessException race) {
            // A missing-row SELECT ... FOR UPDATE cannot serialize the first
            // insert under every MySQL isolation level. The winner commits;
            // one retry then locks and observes that row.
            return claimInTransaction(tenantId, key, requestHash, now);
        }
    }

    private ClaimOutcome claimInTransaction(long tenantId, String key, String requestHash, Instant now) {
        return transactionTemplate.execute(status -> {
            List<RecordRow> rows = mapper.lockRecord(tenantId, key);

            Instant expiresAt = now.plus(ttl);
            if (rows.isEmpty()) {
                mapper.insertClaim(tenantId, key, requestHash, STATUS_IN_FLIGHT, expiresAt);
                return new ClaimOutcome.New();
            }

            RecordRow existing = rows.get(0);
            if (!existing.expiresAt().isAfter(now)) {
                // Expired claim: take over under the row lock.
                mapper.takeOverExpiredClaim(
                        tenantId, key, requestHash, STATUS_IN_FLIGHT, expiresAt);
                return new ClaimOutcome.New();
            }
            if (!existing.requestHash().equals(requestHash)) {
                return new ClaimOutcome.DifferentBody();
            }
            if (STATUS_IN_FLIGHT.equals(existing.status())) {
                return new ClaimOutcome.InFlight();
            }
            Integer statusCode = existing.responseStatus() == null ? 0 : existing.responseStatus();
            return new ClaimOutcome.Replay(new CachedHttpResponse(
                    statusCode,
                    existing.responseContentType(),
                    existing.responseBody() == null ? new byte[0] : existing.responseBody()));
        });
    }

    @Override
    public void commit(long tenantId, String key, CachedHttpResponse response, Instant now) {
        mapper.commitClaim(
                tenantId, key, STATUS_COMMITTED, response.status(), response.contentType(),
                response.body(), STATUS_IN_FLIGHT);
    }

    @Override
    public void release(long tenantId, String key) {
        // Only an open claim is released; a committed replay must survive.
        mapper.releaseClaim(tenantId, key, STATUS_IN_FLIGHT);
    }
}
