package com.enterprise.iam.identity.application.service;

import com.enterprise.iam.identity.application.port.out.TenantRepository;
import com.enterprise.iam.identity.domain.model.Tenant;
import com.enterprise.iam.identity.domain.model.TenantStatus;

import java.util.Objects;

public class EnableTenantUseCase {

    private final TenantRepository tenantRepository;

    public EnableTenantUseCase(TenantRepository tenantRepository) {
        this.tenantRepository = Objects.requireNonNull(tenantRepository);
    }

    public void enableTenant(long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("租户不存在: " + tenantId));
        if (tenant.status() == TenantStatus.ACTIVE) {
            return;
        }
        tenantRepository.updateStatus(tenantId, TenantStatus.ACTIVE, tenant.version());
    }
}
