package com.enterprise.iam.identity.application.service;

import com.enterprise.iam.identity.application.port.out.TenantRepository;
import com.enterprise.iam.identity.domain.model.Tenant;

import java.util.List;
import java.util.Objects;

public class ListTenantsUseCase {

    private final TenantRepository tenantRepository;

    public ListTenantsUseCase(TenantRepository tenantRepository) {
        this.tenantRepository = Objects.requireNonNull(tenantRepository);
    }

    public List<Tenant> listTenants(String keyword, String status) {
        return tenantRepository.findAll(keyword, status);
    }
}
