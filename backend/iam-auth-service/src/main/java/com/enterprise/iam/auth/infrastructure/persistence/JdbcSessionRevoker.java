package com.enterprise.iam.auth.infrastructure.persistence;

import com.enterprise.iam.auth.application.port.out.SessionRevoker;
import com.enterprise.iam.auth.infrastructure.persistence.mapper.AuthPersistenceMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class JdbcSessionRevoker implements SessionRevoker {

    private final AuthPersistenceMapper mapper;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public JdbcSessionRevoker(
            AuthPersistenceMapper mapper,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.mapper = Objects.requireNonNull(mapper);
        this.transaction = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public void revokeSession(long tenantId, long sessionId) {
        transaction.executeWithoutResult(status -> {
            Instant now = Instant.now(clock);
            mapper.revokeSession(tenantId, sessionId, now);
            mapper.revokeSessionRefreshTokens(tenantId, sessionId, now);
        });
    }
}
