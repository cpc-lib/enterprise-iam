package com.enterprise.iam.organization.application.service;

import com.enterprise.iam.organization.application.port.out.OrganizationRepository;
import com.enterprise.iam.organization.application.port.out.PositionRepository;
import com.enterprise.iam.organization.domain.model.Organization;
import com.enterprise.iam.organization.domain.model.OrganizationStatus;
import com.enterprise.iam.organization.domain.model.Position;

import java.util.Objects;

public class UpdatePositionUseCase {

    private final PositionRepository positionRepository;
    private final OrganizationRepository organizationRepository;

    public UpdatePositionUseCase(
            PositionRepository positionRepository,
            OrganizationRepository organizationRepository) {
        this.positionRepository = Objects.requireNonNull(positionRepository);
        this.organizationRepository = Objects.requireNonNull(organizationRepository);
    }

    public Position update(
            long tenantId,
            long positionId,
            String positionCode,
            String positionName,
            Long organizationId,
            int sortOrder,
            String description,
            long expectedVersion) {
        Position existing = positionRepository.findById(tenantId, positionId)
                .orElseThrow(() -> new IllegalArgumentException("position not found"));

        if (existing.version() != expectedVersion) {
            throw new IllegalStateException("IAM_OPTIMISTIC_LOCK_ERROR: position version mismatch");
        }

        if (!existing.positionCode().equals(positionCode)) {
            positionRepository.findByCode(tenantId, positionCode).ifPresent(other -> {
                if (other.id() != positionId) {
                    throw new IllegalStateException("position code already exists");
                }
            });
        }

        if (organizationId != null) {
            Organization org = organizationRepository.findById(tenantId, organizationId)
                    .orElseThrow(() -> new IllegalArgumentException("organization not found"));
            if (org.status() != OrganizationStatus.ACTIVE) {
                throw new IllegalStateException("cannot bind position to inactive organization");
            }
        }

        Position updated = new Position(
                positionId,
                tenantId,
                positionCode.trim(),
                positionName.trim(),
                organizationId,
                sortOrder,
                description,
                existing.status(),
                expectedVersion,
                existing.createdAt(),
                existing.updatedAt());

        boolean ok = positionRepository.update(updated, expectedVersion);
        if (!ok) {
            throw new IllegalStateException("failed to update position");
        }

        return positionRepository.findById(tenantId, positionId)
                .orElseThrow(() -> new IllegalStateException("updated position could not be reloaded"));
    }
}
