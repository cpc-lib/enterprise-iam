package com.enterprise.iam.organization.application.service;

import com.enterprise.iam.organization.application.command.CreateOrganizationCommand;
import com.enterprise.iam.organization.application.model.CreateOrganizationResult;
import com.enterprise.iam.organization.application.port.out.OrganizationRepository;
import com.enterprise.iam.organization.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.organization.domain.model.Organization;
import com.enterprise.iam.organization.domain.model.OrganizationStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class CreateOrganizationUseCase {

    private final OrganizationRepository organizationRepository;
    private final PositiveIdGenerator idGenerator;
    private final Clock clock;

    public CreateOrganizationUseCase(
            OrganizationRepository organizationRepository,
            PositiveIdGenerator idGenerator,
            Clock clock) {
        this.organizationRepository = Objects.requireNonNull(organizationRepository);
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.clock = Objects.requireNonNull(clock);
    }

    public CreateOrganizationResult create(CreateOrganizationCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (organizationRepository.findByCode(command.tenantId(), command.orgCode()).isPresent()) {
            throw new IllegalStateException("organization code already exists");
        }

        Instant now = clock.instant();
        long organizationId = idGenerator.nextId();

        int depth;
        String materializedPath;
        if (command.parentId() == null) {
            depth = 0;
            materializedPath = "/" + organizationId + "/";
        } else {
            Organization parent = organizationRepository.findById(command.tenantId(), command.parentId())
                    .orElseThrow(() -> new IllegalArgumentException("parent organization not found"));
            if (parent.status() != OrganizationStatus.ACTIVE) {
                throw new IllegalStateException("parent organization is not active");
            }
            depth = parent.depth() + 1;
            materializedPath = parent.materializedPath() + organizationId + "/";
        }

        Organization organization = new Organization(
                organizationId,
                command.tenantId(),
                command.orgCode(),
                command.orgName(),
                command.parentId(),
                materializedPath,
                depth,
                OrganizationStatus.ACTIVE,
                0,
                now,
                now);
        organizationRepository.save(organization);
        return new CreateOrganizationResult(organizationId, OrganizationStatus.ACTIVE, 0);
    }
}
