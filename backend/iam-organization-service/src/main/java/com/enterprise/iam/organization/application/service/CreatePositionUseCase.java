package com.enterprise.iam.organization.application.service;

import com.enterprise.iam.organization.application.port.out.OrganizationRepository;
import com.enterprise.iam.organization.application.port.out.PositionRepository;
import com.enterprise.iam.organization.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.organization.domain.model.Organization;
import com.enterprise.iam.organization.domain.model.OrganizationStatus;
import com.enterprise.iam.organization.domain.model.Position;
import com.enterprise.iam.organization.domain.model.PositionStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public class CreatePositionUseCase {

    private final PositionRepository positionRepository;
    private final OrganizationRepository organizationRepository;
    private final PositiveIdGenerator idGenerator;
    private final Clock clock;

    public CreatePositionUseCase(
            PositionRepository positionRepository,
            OrganizationRepository organizationRepository,
            PositiveIdGenerator idGenerator,
            Clock clock) {
        this.positionRepository = Objects.requireNonNull(positionRepository);
        this.organizationRepository = Objects.requireNonNull(organizationRepository);
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.clock = Objects.requireNonNull(clock);
    }

    public Position create(
            long tenantId,
            String positionCode,
            String positionName,
            Long organizationId,
            int sortOrder,
            String description) {
        if (positionCode == null || positionCode.isBlank()) {
            throw new IllegalArgumentException("positionCode must not be blank");
        }
        if (positionName == null || positionName.isBlank()) {
            throw new IllegalArgumentException("positionName must not be blank");
        }

        if (positionRepository.findByCode(tenantId, positionCode).isPresent()) {
            throw new IllegalStateException("position code already exists");
        }

        if (organizationId != null) {
            Organization org = organizationRepository.findById(tenantId, organizationId)
                    .orElseThrow(() -> new IllegalArgumentException("organization not found"));
            if (org.status() != OrganizationStatus.ACTIVE) {
                throw new IllegalStateException("cannot bind position to inactive organization");
            }
        }

        Instant now = clock.instant();
        long positionId = idGenerator.nextId();
        Position position = new Position(
                positionId,
                tenantId,
                positionCode.trim(),
                positionName.trim(),
                organizationId,
                sortOrder,
                description,
                PositionStatus.ACTIVE,
                0L,
                now,
                now);
        return positionRepository.save(position);
    }
}
