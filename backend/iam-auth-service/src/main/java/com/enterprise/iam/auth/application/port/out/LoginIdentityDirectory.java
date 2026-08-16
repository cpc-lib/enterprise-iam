package com.enterprise.iam.auth.application.port.out;

import com.enterprise.iam.auth.domain.model.IdentityType;
import com.enterprise.iam.auth.domain.model.ResolvedLoginIdentity;

import java.util.Optional;

public interface LoginIdentityDirectory {

    Optional<ResolvedLoginIdentity> findByTenantAndIdentity(
            String tenantCode,
            IdentityType identityType,
            String normalizedIdentity);
}
