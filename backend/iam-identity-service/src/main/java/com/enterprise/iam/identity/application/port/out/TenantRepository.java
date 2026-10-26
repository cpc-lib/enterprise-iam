package com.enterprise.iam.identity.application.port.out;

import com.enterprise.iam.identity.domain.model.Tenant;
import com.enterprise.iam.identity.domain.model.TenantStatus;

import java.util.List;
import java.util.Optional;

public interface TenantRepository {

    Tenant save(Tenant tenant);

    Optional<Tenant> findById(long tenantId);

    Optional<Tenant> findByCode(String tenantCode);

    List<Tenant> findAll(String keyword, String status);

    void updateStatus(long tenantId, TenantStatus status, Long expectedVersion);

    void updateName(long tenantId, String tenantName, Long expectedVersion);
}
