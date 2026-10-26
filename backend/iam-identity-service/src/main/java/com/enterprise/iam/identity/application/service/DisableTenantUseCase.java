package com.enterprise.iam.identity.application.service;

import com.enterprise.iam.identity.application.port.out.TenantRepository;
import com.enterprise.iam.identity.domain.model.Tenant;
import com.enterprise.iam.identity.domain.model.TenantStatus;

import java.util.Objects;

public class DisableTenantUseCase {

    private final TenantRepository tenantRepository;

    public DisableTenantUseCase(TenantRepository tenantRepository) {
        this.tenantRepository = Objects.requireNonNull(tenantRepository);
    }

    public void disableTenant(long tenantId) {
        if (tenantId == 10001L) {
            throw new IllegalArgumentException("系统运营平台为核心根租户，不可禁用");
        }
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("租户不存在: " + tenantId));
        if (tenant.status() == TenantStatus.DISABLED) {
            return;
        }
        tenantRepository.updateStatus(tenantId, TenantStatus.DISABLED, tenant.version());
    }
}
