package com.enterprise.iam.identity.infrastructure.persistence;

import com.enterprise.iam.identity.application.port.out.UserSessionRevocationPort;
import com.enterprise.iam.identity.infrastructure.persistence.mapper.IdentityPersistenceMapper;

import java.time.Instant;
import java.util.Objects;

public class JdbcUserSessionRevocationAdapter implements UserSessionRevocationPort {

    private final IdentityPersistenceMapper mapper;

    public JdbcUserSessionRevocationAdapter(IdentityPersistenceMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public void kickUserSessions(long tenantId, long userId) {
        Instant now = Instant.now();
        mapper.revokeAllUserSessions(tenantId, userId, now);
        mapper.revokeAllUserRefreshTokens(tenantId, userId, now);
    }
}
