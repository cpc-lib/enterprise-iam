package com.enterprise.iam.organization.application.service;

import com.enterprise.iam.organization.application.port.out.OrganizationRepository;
import com.enterprise.iam.organization.domain.model.DepartmentMember;

import java.util.List;
import java.util.Objects;

public class ListOrganizationMembersUseCase {

    private final OrganizationRepository organizationRepository;

    public ListOrganizationMembersUseCase(OrganizationRepository organizationRepository) {
        this.organizationRepository = Objects.requireNonNull(organizationRepository);
    }

    public List<DepartmentMember> listMembers(long tenantId, long organizationId) {
        organizationRepository.findById(tenantId, organizationId)
                .orElseThrow(() -> new IllegalArgumentException("organization not found"));
        return organizationRepository.findMembers(tenantId, organizationId);
    }
}
