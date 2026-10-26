package com.enterprise.iam.auth.application.port.out;

import com.enterprise.iam.auth.domain.model.WebAuthnChallenge;
import com.enterprise.iam.auth.domain.model.WebAuthnCredential;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WebAuthnRepository {

    void saveChallenge(WebAuthnChallenge challenge);

    Optional<WebAuthnChallenge> findPendingChallenge(long tenantId, String challengeId);

    boolean markChallengeConsumed(long tenantId, String challengeId, Instant now);

    WebAuthnCredential saveCredential(WebAuthnCredential credential);

    Optional<WebAuthnCredential> findCredentialByTenantAndCredentialId(long tenantId, String credentialId);

    List<WebAuthnCredential> findCredentialsByUser(long tenantId, long userId);

    boolean updateCredentialSignCount(long tenantId, long id, long newSignCount, Instant lastUsedAt);

    boolean revokeCredential(long tenantId, long userId, long id);
}
