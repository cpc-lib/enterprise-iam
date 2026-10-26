package com.enterprise.iam.outbox;

import com.enterprise.iam.outbox.mapper.OutboxPersistenceMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** MySQL 8 queue repository using short READ COMMITTED SKIP LOCKED claims. */
public final class JdbcOutboxRepository implements OutboxRepository {

    private static final Pattern OWNER = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");
    private static final Pattern ERROR_CODE = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");

    private final OutboxPersistenceMapper mapper;
    private final TransactionTemplate claimTransaction;

    public JdbcOutboxRepository(
            OutboxPersistenceMapper mapper,
            PlatformTransactionManager transactionManager) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.claimTransaction = new TransactionTemplate(Objects.requireNonNull(
                transactionManager, "transactionManager must not be null"));
        this.claimTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.claimTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.claimTransaction.setTimeout(5);
    }

    @Override
    public List<OutboxEvent> claimBatch(
            String claimOwner,
            Instant now,
            Instant claimUntil,
            int batchSize) {
        String owner = requireOwner(claimOwner);
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(claimUntil, "claimUntil must not be null");
        if (!claimUntil.isAfter(now)) {
            throw new IllegalArgumentException("claimUntil must be after now");
        }
        if (batchSize < 1 || batchSize > 100) {
            throw new IllegalArgumentException("batchSize must be 1..100");
        }
        List<OutboxEvent> result = claimTransaction.execute(status ->
                claimInsideTransaction(owner, now, claimUntil, batchSize));
        return result == null ? List.of() : result;
    }

    private List<OutboxEvent> claimInsideTransaction(
            String owner,
            Instant now,
            Instant claimUntil,
            int batchSize) {
        List<Long> ids = mapper.claimableIds(now, batchSize);
        if (ids.isEmpty()) {
            return List.of();
        }
        int updated = mapper.claimRows(ids, owner, claimUntil);
        if (updated != ids.size()) {
            throw new IllegalStateException("outbox claim update count is inconsistent");
        }
        return mapper.loadClaimedRows(ids, owner);
    }

    @Override
    public void markPublished(long id, String claimOwner, Instant publishedAt) {
        OutboxEvent.requirePositive(id, "id");
        int updated = mapper.markPublished(
                id,
                requireOwner(claimOwner),
                Objects.requireNonNull(publishedAt, "publishedAt must not be null"));
        requireOwnedUpdate(id, updated);
    }

    @Override
    public void reschedule(
            long id,
            String claimOwner,
            int retryCount,
            Instant nextRetryAt,
            String errorCode) {
        OutboxEvent.requirePositive(id, "id");
        requireRetryCount(retryCount);
        int updated = mapper.reschedule(
                id,
                requireOwner(claimOwner),
                retryCount,
                Objects.requireNonNull(nextRetryAt, "nextRetryAt must not be null"),
                requireErrorCode(errorCode));
        requireOwnedUpdate(id, updated);
    }

    @Override
    public void markDead(long id, String claimOwner, int retryCount, String errorCode) {
        OutboxEvent.requirePositive(id, "id");
        requireRetryCount(retryCount);
        int updated = mapper.markDead(
                id, requireOwner(claimOwner), retryCount, requireErrorCode(errorCode));
        requireOwnedUpdate(id, updated);
    }

    private static String requireOwner(String value) {
        if (value == null || !OWNER.matcher(value).matches()) {
            throw new IllegalArgumentException("claimOwner is invalid");
        }
        return value;
    }

    private static String requireErrorCode(String value) {
        if (value == null || !ERROR_CODE.matcher(value).matches()) {
            throw new IllegalArgumentException("errorCode is invalid");
        }
        return value;
    }

    private static void requireRetryCount(int value) {
        if (value < 1 || value > 20) {
            throw new IllegalArgumentException("retryCount must be 1..20");
        }
    }

    private static void requireOwnedUpdate(long id, int updated) {
        if (updated != 1) {
            throw new OutboxLeaseLostException(id);
        }
    }
}
