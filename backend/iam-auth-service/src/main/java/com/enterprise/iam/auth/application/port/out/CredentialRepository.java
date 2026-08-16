package com.enterprise.iam.auth.application.port.out;

import com.enterprise.iam.auth.domain.model.CredentialSnapshot;

import java.util.Optional;

public interface CredentialRepository {

    Optional<CredentialSnapshot> findByTenantAndUser(long tenantId, long userId);

    /**
     * Executes the bounded database path used when no identity was resolved. The
     * adapter must not return or log whether the tenant exists.
     */
    void performEnumerationResistantDummyLookup(String tenantCode);
}
