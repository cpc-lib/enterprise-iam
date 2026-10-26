package com.enterprise.iam.organization.application.service;

import com.enterprise.iam.organization.application.port.out.OrganizationRepository;
import com.enterprise.iam.organization.domain.model.DepartmentNode;
import com.enterprise.iam.organization.domain.model.Organization;

import java.util.Objects;
import java.util.Optional;

public class GetOrganizationDetailUseCase {

    private final OrganizationRepository organizationRepository;

    public GetOrganizationDetailUseCase(OrganizationRepository organizationRepository) {
        this.organizationRepository = Objects.requireNonNull(organizationRepository);
    }

    public Optional<DepartmentNode> getDetail(long tenantId, long organizationId) {
        return organizationRepository.findById(tenantId, organizationId)
                .map(org -> {
                    int memberCount = organizationRepository.countMembers(tenantId, org.id());
                    return new DepartmentNode(
                            org.id(),
                            org.tenantId(),
                            org.orgCode(),
                            org.orgName(),
                            org.parentId(),
                            org.materializedPath(),
                            org.depth(),
                            org.status(),
                            org.version(),
                            memberCount,
                            org.createdAt(),
                            org.updatedAt());
                });
    }
}
