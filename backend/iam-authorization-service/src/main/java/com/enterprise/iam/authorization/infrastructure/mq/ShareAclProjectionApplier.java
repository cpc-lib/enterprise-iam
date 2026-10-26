package com.enterprise.iam.authorization.infrastructure.mq;

import com.enterprise.iam.authorization.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.authorization.infrastructure.persistence.mapper.ShareAclProjectionMapper;
import com.enterprise.iam.authorization.infrastructure.persistence.row.ShareFieldGrantWriteRow;
import com.enterprise.iam.authorization.infrastructure.persistence.row.ShareProjectionWriteRow;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Idempotently materializes share ACL events into the local projection and
 * advances the per-resource security epoch fence. Every event is applied in
 * one local transaction: rows first, then expected/applied epochs, so a
 * crash either commits the whole event or leaves the fence fail-closed until
 * the broker redelivers. Epoch-monotonic SQL makes duplicate or out-of-order
 * deliveries converge without resurrecting revoked rows. SQL is owned by the
 * MyBatis mapper XML so this component only coordinates the transaction.
 */
public final class ShareAclProjectionApplier {

    static final String TARGET_USER = "USER";
    static final String TARGET_TEAM = "TEAM";
    static final String TARGET_ROLE = "ROLE";
    static final String TARGET_TEAM_ROLE = "TEAM_ROLE";

    private final ShareAclProjectionMapper mapper;
    private final TransactionTemplate transactionTemplate;
    private final PositiveIdGenerator idGenerator;
    private final Clock clock;

    public ShareAclProjectionApplier(
            ShareAclProjectionMapper mapper,
            TransactionTemplate transactionTemplate,
            PositiveIdGenerator idGenerator,
            Clock clock) {
        this.mapper = Objects.requireNonNull(mapper);
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate);
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.clock = Objects.requireNonNull(clock);
    }

    public record GrantedMessage(
            long tenantId,
            long resourceId,
            long shareId,
            String resourceInstanceKey,
            String targetType,
            long targetId,
            List<Long> operationIds,
            Instant startTime,
            Instant expireTime,
            long epoch,
            List<FieldGrantMessage> fieldGrants,
            String scopeType,
            String folderPath,
            String collectionResourceIdsJson) {

        public GrantedMessage {
            fieldGrants = fieldGrants == null ? List.of() : List.copyOf(fieldGrants);
            scopeType = scopeType == null || scopeType.isBlank() ? "INSTANCE" : scopeType;
        }

        public GrantedMessage(
                long tenantId,
                long resourceId,
                long shareId,
                String resourceInstanceKey,
                String targetType,
                long targetId,
                List<Long> operationIds,
                Instant startTime,
                Instant expireTime,
                long epoch,
                List<FieldGrantMessage> fieldGrants) {
            this(tenantId, resourceId, shareId, resourceInstanceKey, targetType, targetId,
                    operationIds, startTime, expireTime, epoch, fieldGrants,
                    "INSTANCE", null, null);
        }
    }

    public record RevokedMessage(
            long tenantId,
            long resourceId,
            long shareId,
            String targetType,
            long targetId,
            List<Long> operationIds,
            long epoch) {
    }

    public record FieldGrantMessage(
            long operationId,
            long fieldId,
            boolean readable,
            boolean writable,
            boolean hidden,
            Long maskStrategyId) {
    }

    public void applyGranted(GrantedMessage message) {
        Objects.requireNonNull(message, "message must not be null");
        validateTarget(message.targetType());
        validateOperations(message.operationIds());
        transactionTemplate.executeWithoutResult(status -> {
            Instant now = clock.instant();
            for (Long operationId : message.operationIds()) {
                mapper.upsertGranted(new ShareProjectionWriteRow(
                        idGenerator.nextId(),
                        message.tenantId(),
                        message.shareId(),
                        message.resourceId(),
                        message.resourceInstanceKey(),
                        message.targetType(),
                        message.targetId(),
                        operationId,
                        message.startTime(),
                        message.expireTime(),
                        message.epoch(),
                        now,
                        message.scopeType(),
                        message.folderPath(),
                        message.collectionResourceIdsJson()));
            }
            if (!message.fieldGrants().isEmpty()) {
                mapper.deleteFieldGrantsBeforeEpoch(
                        message.tenantId(), message.shareId(), message.epoch());
                for (FieldGrantMessage fg : message.fieldGrants()) {
                    mapper.upsertFieldGrant(new ShareFieldGrantWriteRow(
                            idGenerator.nextId(),
                            message.tenantId(),
                            message.shareId(),
                            fg.operationId(),
                            fg.fieldId(),
                            fg.readable(),
                            fg.writable(),
                            fg.hidden(),
                            fg.maskStrategyId(),
                            message.epoch(),
                            now));
                }
            }
            advanceEpoch(message.tenantId(), message.resourceId(), message.epoch(), now);
        });
    }

    public void applyRevoked(RevokedMessage message) {
        Objects.requireNonNull(message, "message must not be null");
        validateTarget(message.targetType());
        validateOperations(message.operationIds());
        transactionTemplate.executeWithoutResult(status -> {
            Instant now = clock.instant();
            mapper.revoke(
                    message.epoch(), now, message.tenantId(), message.shareId(),
                    message.targetType(), message.targetId(), message.operationIds());
            mapper.deleteFieldGrantsBeforeEpoch(
                    message.tenantId(), message.shareId(), message.epoch());
            advanceEpoch(message.tenantId(), message.resourceId(), message.epoch(), now);
        });
    }

    private void advanceEpoch(long tenantId, long resourceId, long epoch, Instant now) {
        mapper.advanceExpectedEpoch(tenantId, resourceId, epoch, now);
        mapper.advanceAppliedEpoch(tenantId, resourceId, epoch, now);
    }

    private static void validateTarget(String targetType) {
        if (!TARGET_USER.equals(targetType)
                && !TARGET_TEAM.equals(targetType)
                && !TARGET_ROLE.equals(targetType)
                && !TARGET_TEAM_ROLE.equals(targetType)) {
            throw new IllegalArgumentException("targetType must be USER, TEAM, ROLE, or TEAM_ROLE");
        }
    }

    private static void validateOperations(List<Long> operationIds) {
        if (operationIds == null || operationIds.isEmpty()) {
            throw new IllegalArgumentException("operationIds must not be empty");
        }
    }
}
