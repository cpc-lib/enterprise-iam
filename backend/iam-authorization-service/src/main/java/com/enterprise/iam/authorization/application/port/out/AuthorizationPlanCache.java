package com.enterprise.iam.authorization.application.port.out;

import com.enterprise.iam.authorization.domain.model.AuthorizationRequest;
import com.enterprise.iam.authorization.domain.model.AuthorizationResult;

import java.util.Optional;

/**
 * L1 decision plan cache. Implementations are optional and default-off; the
 * engine and use cases work without them. {@link #evictTenant(long)} is
 * invoked after tenant permission-version bumps so cached plans never outlive
 * a role/permission change.
 */
public interface AuthorizationPlanCache {

    Optional<AuthorizationResult> cachedDecision(AuthorizationRequest request);

    void storeDecision(AuthorizationRequest request, AuthorizationResult result);

    void evictTenant(long tenantId);
}
