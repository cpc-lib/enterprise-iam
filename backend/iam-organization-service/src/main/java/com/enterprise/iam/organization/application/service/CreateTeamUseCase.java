package com.enterprise.iam.organization.application.service;

import com.enterprise.iam.organization.application.command.CreateTeamCommand;
import com.enterprise.iam.organization.application.model.CreateTeamResult;
import com.enterprise.iam.organization.application.port.out.OrganizationRepository;
import com.enterprise.iam.organization.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.organization.application.port.out.TeamRepository;
import com.enterprise.iam.organization.domain.model.OrganizationStatus;
import com.enterprise.iam.organization.domain.model.Team;
import com.enterprise.iam.organization.domain.model.TeamStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class CreateTeamUseCase {

    private final TeamRepository teamRepository;
    private final OrganizationRepository organizationRepository;
    private final PositiveIdGenerator idGenerator;
    private final Clock clock;

    public CreateTeamUseCase(
            TeamRepository teamRepository,
            OrganizationRepository organizationRepository,
            PositiveIdGenerator idGenerator,
            Clock clock) {
        this.teamRepository = Objects.requireNonNull(teamRepository);
        this.organizationRepository = Objects.requireNonNull(organizationRepository);
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.clock = Objects.requireNonNull(clock);
    }

    public CreateTeamResult create(CreateTeamCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        var organization = organizationRepository.findById(command.tenantId(), command.organizationId())
                .orElseThrow(() -> new IllegalArgumentException("organization not found"));
        if (organization.status() != OrganizationStatus.ACTIVE) {
            throw new IllegalStateException("organization is not active");
        }
        if (teamRepository.findByCode(command.tenantId(), command.teamCode()).isPresent()) {
            throw new IllegalStateException("team code already exists");
        }

        Instant now = clock.instant();
        long teamId = idGenerator.nextId();

        int depth;
        String materializedPath;
        if (command.parentTeamId() == null) {
            depth = 0;
            materializedPath = "/" + teamId + "/";
        } else {
            Team parent = teamRepository.findById(command.tenantId(), command.parentTeamId())
                    .orElseThrow(() -> new IllegalArgumentException("parent team not found"));
            if (parent.status() != TeamStatus.ACTIVE) {
                throw new IllegalStateException("parent team is not active");
            }
            depth = parent.depth() + 1;
            materializedPath = parent.materializedPath() + teamId + "/";
        }

        Team team = new Team(
                teamId,
                command.tenantId(),
                command.organizationId(),
                command.parentTeamId(),
                command.teamCode(),
                command.teamName(),
                materializedPath,
                depth,
                TeamStatus.ACTIVE,
                0,
                now,
                now);
        teamRepository.save(team);
        return new CreateTeamResult(teamId, TeamStatus.ACTIVE, 0);
    }
}
