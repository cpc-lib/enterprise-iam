package com.enterprise.iam.organization.application.service;

import com.enterprise.iam.organization.application.port.out.OrganizationRepository;
import com.enterprise.iam.organization.domain.model.Organization;
import com.enterprise.iam.organization.domain.model.OrganizationStatus;

import java.util.Objects;

public class UpdateOrganizationUseCase {

    private final OrganizationRepository organizationRepository;

    public UpdateOrganizationUseCase(OrganizationRepository organizationRepository) {
        this.organizationRepository = Objects.requireNonNull(organizationRepository);
    }

    public Organization update(
            long tenantId,
            long organizationId,
            String orgCode,
            String orgName,
            Long parentId,
            long expectedVersion) {
        Organization existing = organizationRepository.findById(tenantId, organizationId)
                .orElseThrow(() -> new IllegalArgumentException("organization not found"));

        if (existing.version() != expectedVersion) {
            throw new IllegalStateException("IAM_OPTIMISTIC_LOCK_ERROR: organization version mismatch");
        }

        if (parentId != null && parentId == organizationId) {
            throw new IllegalArgumentException("organization cannot be its own parent");
        }

        // Check code uniqueness if changed
        if (!existing.orgCode().equals(orgCode)) {
            organizationRepository.findByCode(tenantId, orgCode).ifPresent(other -> {
                if (other.id() != organizationId) {
                    throw new IllegalStateException("organization code already exists");
                }
            });
        }

        int depth;
        String materializedPath;
        if (parentId == null) {
            depth = 0;
            materializedPath = "/" + organizationId + "/";
        } else {
            Organization parent = organizationRepository.findById(tenantId, parentId)
                    .orElseThrow(() -> new IllegalArgumentException("parent organization not found"));
            if (parent.materializedPath().contains("/" + organizationId + "/")) {
                throw new IllegalStateException("circular reference detected in organization hierarchy");
            }
            depth = parent.depth() + 1;
            materializedPath = parent.materializedPath() + organizationId + "/";
        }

        if (depth > 32) {
            throw new IllegalStateException("organization hierarchy depth exceeds maximum allowed of 32");
        }

        boolean ok = organizationRepository.update(
                tenantId,
                organizationId,
                orgCode,
                orgName,
                parentId,
                materializedPath,
                depth,
                expectedVersion);
        if (!ok) {
            throw new IllegalStateException("failed to update organization");
        }

        return organizationRepository.findById(tenantId, organizationId)
                .orElseThrow(() -> new IllegalStateException("updated organization could not be reloaded"));
    }
}
