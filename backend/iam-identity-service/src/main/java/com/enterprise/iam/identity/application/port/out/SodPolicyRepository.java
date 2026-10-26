package com.enterprise.iam.identity.application.port.out;

import com.enterprise.iam.identity.domain.model.SodPolicy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SodPolicyRepository {

    SodPolicy save(SodPolicy policy);

    List<SodPolicy> findByTenant(long tenantId);

    Optional<SodPolicy> findById(long tenantId, long id);

    boolean updateStatus(long tenantId, long id, String status, Instant now);
}
