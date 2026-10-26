package com.enterprise.iam.auth.infrastructure.persistence;

import com.enterprise.iam.auth.application.port.out.SessionQueryRepository;
import com.enterprise.iam.auth.domain.model.ActiveSession;
import com.enterprise.iam.auth.infrastructure.persistence.mapper.AuthPersistenceMapper;
import com.enterprise.iam.auth.infrastructure.persistence.mapper.AuthPersistenceMapper.ActiveSessionRow;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class JdbcSessionQueryRepository implements SessionQueryRepository {

    private final AuthPersistenceMapper mapper;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public JdbcSessionQueryRepository(
            AuthPersistenceMapper mapper,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.transaction = new TransactionTemplate(Objects.requireNonNull(transactionManager, "transactionManager must not be null"));
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public List<ActiveSession> findSessions(long tenantId, Long userId, String status, int offset, int limit) {
        List<ActiveSessionRow> rows = mapper.selectSessionsPage(tenantId, userId, status, offset, limit);
        return rows.stream().map(this::toDomain).toList();
    }

    @Override
    public long countSessions(long tenantId, Long userId, String status) {
        return mapper.countSessions(tenantId, userId, status);
    }

    @Override
    public boolean revokeSession(long tenantId, long sessionId, String reason) {
        return Boolean.TRUE.equals(transaction.execute(status -> {
            Instant now = Instant.now(clock);
            int sessionUpdated = mapper.revokeSessionWithReason(tenantId, sessionId, reason, now);
            mapper.revokeSessionRefreshTokensWithReason(tenantId, sessionId, reason, now);
            return sessionUpdated > 0;
        }));
    }

    private ActiveSession toDomain(ActiveSessionRow r) {
        return new ActiveSession(
                r.id(),
                r.tenantId(),
                r.sessionId(),
                r.userId(),
                r.status(),
                r.deviceType() != null ? r.deviceType() : "PC",
                r.lastAccessAt(),
                r.idleExpireAt(),
                r.absoluteExpireAt(),
                r.lastStrongAuthAt(),
                r.revokedAt(),
                r.revokeReason(),
                r.sessionVersion(),
                r.createdAt(),
                r.updatedAt()
        );
    }
}
