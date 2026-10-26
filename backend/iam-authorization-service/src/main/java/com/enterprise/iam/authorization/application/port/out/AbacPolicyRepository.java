package com.enterprise.iam.authorization.application.port.out;

import com.enterprise.iam.authorization.domain.model.AbacPolicy;

import java.util.List;
import java.util.Optional;

public interface AbacPolicyRepository {

    AbacPolicy save(AbacPolicy policy);

    List<AbacPolicy> findByTenant(long tenantId);

    List<AbacPolicy> findActiveByTenantAndResource(long tenantId, String resourceType, String operation);

    Optional<AbacPolicy> findById(long tenantId, long id);

    boolean updateStatus(long tenantId, long id, String status);

    boolean deleteById(long tenantId, long id);
}
