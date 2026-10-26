package com.enterprise.iam.identity.application.service;

import com.enterprise.iam.identity.application.port.out.TenantRepository;
import com.enterprise.iam.identity.domain.model.Tenant;

import java.util.Objects;

public class UpdateTenantUseCase {

    private final TenantRepository tenantRepository;

    public UpdateTenantUseCase(TenantRepository tenantRepository) {
        this.tenantRepository = Objects.requireNonNull(tenantRepository);
    }

    public void updateTenantName(long tenantId, String tenantName) {
        if (tenantName == null || tenantName.isBlank()) {
            throw new IllegalArgumentException("租户名称不能为空");
        }
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("租户不存在: " + tenantId));
        tenantRepository.updateName(tenantId, tenantName.trim(), tenant.version());
    }
}
