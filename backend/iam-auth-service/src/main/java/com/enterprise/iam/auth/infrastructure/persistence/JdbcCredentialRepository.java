package com.enterprise.iam.auth.infrastructure.persistence;

import com.enterprise.iam.auth.application.port.out.CredentialRepository;
import com.enterprise.iam.auth.domain.model.CredentialSnapshot;
import com.enterprise.iam.auth.infrastructure.persistence.mapper.AuthPersistenceMapper;

import java.util.Objects;
import java.util.Optional;

public final class JdbcCredentialRepository implements CredentialRepository {

    private final AuthPersistenceMapper mapper;

    public JdbcCredentialRepository(AuthPersistenceMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Optional<CredentialSnapshot> findByTenantAndUser(long tenantId, long userId) {
        AuthPersistenceMapper.CredentialRow row = mapper.findCredential(tenantId, userId);
        return Optional.ofNullable(row).map(credential -> new CredentialSnapshot(
                credential.tenantId(),
                credential.userId(),
                credential.passwordPhc(),
                "ACTIVE".equals(credential.status()),
                credential.lockUntil()));
    }

    @Override
    public void performEnumerationResistantDummyLookup(String tenantCode) {
        mapper.performEnumerationResistantDummyLookup();
    }
}
