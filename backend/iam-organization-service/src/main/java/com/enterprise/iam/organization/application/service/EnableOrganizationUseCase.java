package com.enterprise.iam.organization.application.service;

import com.enterprise.iam.organization.application.port.out.OrganizationRepository;
import com.enterprise.iam.organization.domain.model.Organization;
import com.enterprise.iam.organization.domain.model.OrganizationStatus;

import java.util.Objects;

public class EnableOrganizationUseCase {

    private final OrganizationRepository organizationRepository;

    public EnableOrganizationUseCase(OrganizationRepository organizationRepository) {
        this.organizationRepository = Objects.requireNonNull(organizationRepository);
    }

    public Organization enable(long tenantId, long organizationId, long expectedVersion) {
        Organization existing = organizationRepository.findById(tenantId, organizationId)
                .orElseThrow(() -> new IllegalArgumentException("organization not found"));

        if (existing.version() != expectedVersion) {
            throw new IllegalStateException("IAM_OPTIMISTIC_LOCK_ERROR: organization version mismatch");
        }

        // If parent exists, verify parent is ACTIVE
        if (existing.parentId() != null) {
            Organization parent = organizationRepository.findById(tenantId, existing.parentId())
                    .orElseThrow(() -> new IllegalStateException("parent organization not found"));
            if (parent.status() != OrganizationStatus.ACTIVE) {
                throw new IllegalStateException("cannot enable organization when parent organization is disabled");
            }
        }

        boolean ok = organizationRepository.updateStatus(
                tenantId, organizationId, OrganizationStatus.ACTIVE, expectedVersion);
        if (!ok) {
            throw new IllegalStateException("failed to enable organization");
        }

        return organizationRepository.findById(tenantId, organizationId)
                .orElseThrow(() -> new IllegalStateException("enabled organization could not be reloaded"));
    }
}
