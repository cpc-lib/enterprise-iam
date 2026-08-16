package com.enterprise.iam.outbox;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** MySQL 8 queue repository using short READ COMMITTED SKIP LOCKED claims. */
public final class JdbcOutboxRepository implements OutboxRepository {

    private static final Pattern OWNER = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");
    private static final Pattern ERROR_CODE = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");

    static final String CLAIM_IDS_SQL = """
            SELECT id
            FROM sys_outbox_event
            WHERE (event_status = 'PENDING'
                    AND (next_retry_at IS NULL OR next_retry_at <= :now))
               OR (event_status = 'CLAIMED' AND claim_until <= :now)
            ORDER BY id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """;

    static final String CLAIM_ROWS_SQL = """
            UPDATE sys_outbox_event
            SET event_status = 'CLAIMED', claim_owner = :owner,
                claim_until = :claimUntil, next_retry_at = NULL,
                version = version + 1
            WHERE id IN (:ids)
            """;

    static final String LOAD_CLAIMED_SQL = """
            SELECT id, event_id, tenant_id, aggregate_type, aggregate_id,
                   aggregate_version, event_type, schema_version, payload,
                   retry_count, created_at
            FROM sys_outbox_event
            WHERE id IN (:ids) AND event_status = 'CLAIMED' AND claim_owner = :owner
            ORDER BY id
            """;

    private static final String MARK_PUBLISHED_SQL = """
            UPDATE sys_outbox_event
            SET event_status = 'PUBLISHED', published_at = ?, claim_owner = NULL,
                claim_until = NULL, next_retry_at = NULL, last_error_code = NULL,
                version = version + 1
            WHERE id = ? AND event_status = 'CLAIMED' AND claim_owner = ?
            """;

    private static final String RESCHEDULE_SQL = """
            UPDATE sys_outbox_event
            SET event_status = 'PENDING', retry_count = ?, next_retry_at = ?,
                claim_owner = NULL, claim_until = NULL, last_error_code = ?,
                version = version + 1
            WHERE id = ? AND event_status = 'CLAIMED' AND claim_owner = ?
            """;

    private static final String MARK_DEAD_SQL = """
            UPDATE sys_outbox_event
            SET event_status = 'DEAD', retry_count = ?, next_retry_at = NULL,
                claim_owner = NULL, claim_until = NULL, last_error_code = ?,
                version = version + 1
            WHERE id = ? AND event_status = 'CLAIMED' AND claim_owner = ?
            """;

    private static final RowMapper<OutboxEvent> ROW_MAPPER =
            JdbcOutboxRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final TransactionTemplate claimTransaction;

    public JdbcOutboxRepository(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
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
        MapSqlParameterSource claimParameters = new MapSqlParameterSource()
                .addValue("now", Timestamp.from(now))
                .addValue("batchSize", batchSize);
        List<Long> ids = namedJdbcTemplate.queryForList(
                CLAIM_IDS_SQL, claimParameters, Long.class);
        if (ids.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource ownership = new MapSqlParameterSource()
                .addValue("ids", ids)
                .addValue("owner", owner)
                .addValue("claimUntil", Timestamp.from(claimUntil));
        int updated = namedJdbcTemplate.update(CLAIM_ROWS_SQL, ownership);
        if (updated != ids.size()) {
            throw new IllegalStateException("outbox claim update count is inconsistent");
        }
        return namedJdbcTemplate.query(LOAD_CLAIMED_SQL, ownership, ROW_MAPPER);
    }

    @Override
    public void markPublished(long id, String claimOwner, Instant publishedAt) {
        OutboxEvent.requirePositive(id, "id");
        int updated = jdbcTemplate.update(
                MARK_PUBLISHED_SQL,
                Timestamp.from(Objects.requireNonNull(
                        publishedAt, "publishedAt must not be null")),
                id,
                requireOwner(claimOwner));
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
        int updated = jdbcTemplate.update(
                RESCHEDULE_SQL,
                retryCount,
                Timestamp.from(Objects.requireNonNull(
                        nextRetryAt, "nextRetryAt must not be null")),
                requireErrorCode(errorCode),
                id,
                requireOwner(claimOwner));
        requireOwnedUpdate(id, updated);
    }

    @Override
    public void markDead(long id, String claimOwner, int retryCount, String errorCode) {
        OutboxEvent.requirePositive(id, "id");
        requireRetryCount(retryCount);
        int updated = jdbcTemplate.update(
                MARK_DEAD_SQL,
                retryCount,
                requireErrorCode(errorCode),
                id,
                requireOwner(claimOwner));
        requireOwnedUpdate(id, updated);
    }

    private static OutboxEvent mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new OutboxEvent(
                resultSet.getLong("id"),
                resultSet.getLong("event_id"),
                resultSet.getLong("tenant_id"),
                resultSet.getString("aggregate_type"),
                resultSet.getLong("aggregate_id"),
                resultSet.getLong("aggregate_version"),
                resultSet.getString("event_type"),
                resultSet.getInt("schema_version"),
                resultSet.getString("payload"),
                resultSet.getInt("retry_count"),
                resultSet.getTimestamp("created_at").toInstant());
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
