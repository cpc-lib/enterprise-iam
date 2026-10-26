package com.enterprise.iam.auth.infrastructure.persistence;

import com.enterprise.iam.auth.application.port.out.LoginAuditOutboxAppender;
import com.enterprise.iam.auth.application.port.out.OrphanLoginSessionReconciler;
import com.enterprise.iam.auth.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.auth.application.port.out.SessionProjectionOutboxAppender;
import com.enterprise.iam.auth.infrastructure.config.SessionIssuanceProperties;
import com.enterprise.iam.auth.infrastructure.persistence.mapper.AuthPersistenceMapper;
import com.enterprise.iam.auth.infrastructure.persistence.mapper.AuthPersistenceMapper.StoredLoginDeliveryRow;
import com.enterprise.iam.auth.infrastructure.persistence.mapper.AuthPersistenceMapper.StoredSessionRow;
import com.enterprise.iam.common.security.session.SessionProjectionStatus;
import com.enterprise.iam.common.security.session.SessionSecurityProjection;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Atomically revokes unconfirmed login sessions and emits terminal security facts. */
public final class TransactionalJdbcOrphanLoginSessionReconciler
        implements OrphanLoginSessionReconciler {

    private final AuthPersistenceMapper mapper;
    private final TransactionTemplate transaction;
    private final PositiveIdGenerator idGenerator;
    private final SessionProjectionOutboxAppender projectionAppender;
    private final LoginAuditOutboxAppender auditAppender;
    private final Clock clock;
    private final SessionIssuanceProperties properties;

    public TransactionalJdbcOrphanLoginSessionReconciler(
            AuthPersistenceMapper mapper,
            PlatformTransactionManager transactionManager,
            PositiveIdGenerator idGenerator,
            SessionProjectionOutboxAppender projectionAppender,
            LoginAuditOutboxAppender auditAppender,
            Clock clock,
            SessionIssuanceProperties properties) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.transaction = new TransactionTemplate(Objects.requireNonNull(
                transactionManager, "transactionManager must not be null"));
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator must not be null");
        this.projectionAppender = Objects.requireNonNull(
                projectionAppender, "projectionAppender must not be null");
        this.auditAppender = Objects.requireNonNull(
                auditAppender, "auditAppender must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        properties.validateEnabled();
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(Math.toIntExact(properties.getTransactionTimeout().toSeconds()));
    }

    @Override
    public int reconcileExpired() {
        Instant now = Instant.ofEpochMilli(clock.instant().toEpochMilli());
        int reconciled = 0;
        for (int index = 0; index < properties.getOrphanReconciliationBatchSize(); index++) {
            Boolean processed = transaction.execute(status -> reconcileNext(now));
            if (!Boolean.TRUE.equals(processed)) {
                break;
            }
            reconciled++;
        }
        return reconciled;
    }

    private boolean reconcileNext(Instant now) {
        List<StoredLoginDeliveryRow> deliveries =
                mapper.findNextExpiredLoginDeliveryForUpdate(now);
        if (deliveries.size() > 1) {
            throw new IllegalStateException("orphan reconciliation selected more than one row");
        }
        if (deliveries.isEmpty()) {
            return false;
        }
        reconcileOne(deliveries.get(0), now);
        return true;
    }

    private void reconcileOne(StoredLoginDeliveryRow delivery, Instant now) {
        if (!"PENDING".equals(delivery.deliveryStatus())) {
            return;
        }
        long tokenVersion = lockTokenVersion(delivery);
        StoredSessionRow session = uniqueSession(mapper.findSessionForUpdate(
                delivery.tenantId(), delivery.sessionId()));
        if (session == null || session.userId() != delivery.userId()) {
            throw new IllegalStateException("login delivery points at a missing or foreign session");
        }

        mapper.revokeOrphanSessionRefreshTokens(
                delivery.tenantId(), delivery.sessionId(), now);
        if ("ACTIVE".equals(session.status())) {
            int revoked = mapper.revokeOrphanSession(
                    delivery.tenantId(), delivery.sessionId(), now);
            if (revoked != 1) {
                throw new IllegalStateException("orphan session revocation did not affect one row");
            }

            long terminalVersion = Math.addExact(session.sessionVersion(), 1);
            projectionAppender.append(
                    idGenerator.nextId(),
                    new SessionSecurityProjection(
                            delivery.tenantId(),
                            delivery.userId(),
                            delivery.sessionId(),
                            tokenVersion,
                            terminalVersion,
                            SessionProjectionStatus.REVOKED,
                            session.idleExpireAt(),
                            session.absoluteExpireAt()),
                    now);
            auditAppender.appendOrphanReconciled(
                    idGenerator.nextId(),
                    delivery.tenantId(),
                    delivery.userId(),
                    delivery.sessionId(),
                    terminalVersion,
                    delivery.requestId(),
                    now);
        }

        int marked = mapper.markLoginDeliveryReconciled(
                delivery.tenantId(), delivery.sessionId(), now);
        if (marked != 1) {
            throw new IllegalStateException("login delivery reconciliation lost its state lock");
        }
    }

    private long lockTokenVersion(StoredLoginDeliveryRow delivery) {
        List<Long> versions = mapper.lockTokenVersion(delivery.tenantId(), delivery.userId());
        if (versions.size() != 1 || versions.get(0) == null || versions.get(0) <= 0) {
            throw new IllegalStateException("authoritative user security state is missing");
        }
        return versions.get(0);
    }

    private static StoredSessionRow uniqueSession(List<StoredSessionRow> rows) {
        if (rows.size() > 1) {
            throw new IllegalStateException("login session is not unique");
        }
        return rows.isEmpty() ? null : rows.get(0);
    }
}
