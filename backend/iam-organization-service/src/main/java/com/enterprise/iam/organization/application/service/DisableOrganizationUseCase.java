package com.enterprise.iam.organization.application.service;

import com.enterprise.iam.organization.application.port.out.OrganizationRepository;
import com.enterprise.iam.organization.domain.model.Organization;
import com.enterprise.iam.organization.domain.model.OrganizationStatus;

import java.util.Objects;

public class DisableOrganizationUseCase {

    private final OrganizationRepository organizationRepository;

    public DisableOrganizationUseCase(OrganizationRepository organizationRepository) {
        this.organizationRepository = Objects.requireNonNull(organizationRepository);
    }

    public Organization disable(long tenantId, long organizationId, long expectedVersion) {
        Organization existing = organizationRepository.findById(tenantId, organizationId)
                .orElseThrow(() -> new IllegalArgumentException("organization not found"));

        if (existing.version() != expectedVersion) {
            throw new IllegalStateException("IAM_OPTIMISTIC_LOCK_ERROR: organization version mismatch");
        }

        // Fail-closed check: cannot disable if child organizations exist
        int childCount = organizationRepository.countChildren(tenantId, organizationId);
        if (childCount > 0) {
            throw new IllegalStateException("cannot disable organization with active child organizations");
        }

        boolean ok = organizationRepository.updateStatus(
                tenantId, organizationId, OrganizationStatus.DISABLED, expectedVersion);
        if (!ok) {
            throw new IllegalStateException("failed to disable organization");
        }

        return organizationRepository.findById(tenantId, organizationId)
                .orElseThrow(() -> new IllegalStateException("disabled organization could not be reloaded"));
    }
}
