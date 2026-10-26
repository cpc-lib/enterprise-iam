package com.enterprise.iam.auth.infrastructure.persistence;

import com.enterprise.iam.auth.application.port.out.WebAuthnRepository;
import com.enterprise.iam.auth.domain.model.WebAuthnCeremonyType;
import com.enterprise.iam.auth.domain.model.WebAuthnChallenge;
import com.enterprise.iam.auth.domain.model.WebAuthnCredential;
import com.enterprise.iam.auth.domain.model.WebAuthnCredentialStatus;
import com.enterprise.iam.auth.infrastructure.persistence.mapper.AuthPersistenceMapper;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class JdbcWebAuthnRepository implements WebAuthnRepository {

    private final AuthPersistenceMapper mapper;

    public JdbcWebAuthnRepository(AuthPersistenceMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    @Override
    public void saveChallenge(WebAuthnChallenge challenge) {
        int rows = mapper.insertWebAuthnChallenge(
                challenge.challengeId(),
                challenge.tenantId(),
                challenge.userId(),
                challenge.username(),
                challenge.ceremonyType().name(),
                challenge.challengeB64(),
                challenge.rpId(),
                challenge.status(),
                challenge.expiresAt(),
                challenge.createdAt());
        if (rows != 1) {
            throw new IllegalStateException("Failed to insert WebAuthn challenge");
        }
    }

    @Override
    public Optional<WebAuthnChallenge> findPendingChallenge(long tenantId, String challengeId) {
        AuthPersistenceMapper.WebAuthnChallengeRow row = mapper.findPendingWebAuthnChallenge(tenantId, challengeId);
        return Optional.ofNullable(row).map(JdbcWebAuthnRepository::toChallenge);
    }

    @Override
    public boolean markChallengeConsumed(long tenantId, String challengeId, Instant now) {
        return mapper.markWebAuthnChallengeConsumed(tenantId, challengeId, now) == 1;
    }

    @Override
    public WebAuthnCredential saveCredential(WebAuthnCredential credential) {
        int rows = mapper.insertWebAuthnCredential(
                credential.id(),
                credential.tenantId(),
                credential.userId(),
                credential.credentialId(),
                credential.publicKeyCose(),
                credential.signCount(),
                credential.aaguid(),
                credential.transports(),
                credential.deviceName(),
                credential.status().name(),
                credential.createdAt(),
                credential.lastUsedAt());
        if (rows != 1) {
            throw new IllegalStateException("Failed to insert WebAuthn credential");
        }
        return credential;
    }

    @Override
    public Optional<WebAuthnCredential> findCredentialByTenantAndCredentialId(long tenantId, String credentialId) {
        AuthPersistenceMapper.WebAuthnCredentialRow row =
                mapper.findWebAuthnCredentialByTenantAndCredentialId(tenantId, credentialId);
        return Optional.ofNullable(row).map(JdbcWebAuthnRepository::toCredential);
    }

    @Override
    public List<WebAuthnCredential> findCredentialsByUser(long tenantId, long userId) {
        return mapper.findWebAuthnCredentialsByUser(tenantId, userId).stream()
                .map(JdbcWebAuthnRepository::toCredential)
                .toList();
    }

    @Override
    public boolean updateCredentialSignCount(long tenantId, long id, long newSignCount, Instant lastUsedAt) {
        return mapper.updateWebAuthnCredentialSignCount(tenantId, id, newSignCount, lastUsedAt) == 1;
    }

    @Override
    public boolean revokeCredential(long tenantId, long userId, long id) {
        return mapper.revokeWebAuthnCredential(tenantId, userId, id) == 1;
    }

    private static WebAuthnChallenge toChallenge(AuthPersistenceMapper.WebAuthnChallengeRow row) {
        return new WebAuthnChallenge(
                row.challengeId(),
                row.tenantId(),
                row.userId(),
                row.username(),
                WebAuthnCeremonyType.valueOf(row.ceremonyType()),
                row.challengeB64(),
                row.rpId(),
                row.status(),
                row.expiresAt(),
                row.createdAt());
    }

    private static WebAuthnCredential toCredential(AuthPersistenceMapper.WebAuthnCredentialRow row) {
        return new WebAuthnCredential(
                row.id(),
                row.tenantId(),
                row.userId(),
                row.credentialId(),
                row.publicKeyCose(),
                row.signCount(),
                row.aaguid(),
                row.transports(),
                row.deviceName(),
                WebAuthnCredentialStatus.valueOf(row.status()),
                row.createdAt(),
                row.lastUsedAt());
    }
}
