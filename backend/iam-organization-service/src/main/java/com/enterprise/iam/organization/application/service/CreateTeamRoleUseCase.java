package com.enterprise.iam.organization.application.service;

import com.enterprise.iam.organization.application.command.CreateTeamRoleCommand;
import com.enterprise.iam.organization.application.model.CreateTeamRoleResult;
import com.enterprise.iam.organization.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.organization.application.port.out.TeamRepository;
import com.enterprise.iam.organization.application.port.out.TeamRoleRepository;
import com.enterprise.iam.organization.domain.model.TeamRole;
import com.enterprise.iam.organization.domain.model.TeamRoleStatus;
import com.enterprise.iam.organization.domain.model.TeamStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class CreateTeamRoleUseCase {

    private final TeamRoleRepository teamRoleRepository;
    private final TeamRepository teamRepository;
    private final PositiveIdGenerator idGenerator;
    private final Clock clock;

    public CreateTeamRoleUseCase(
            TeamRoleRepository teamRoleRepository,
            TeamRepository teamRepository,
            PositiveIdGenerator idGenerator,
            Clock clock) {
        this.teamRoleRepository = Objects.requireNonNull(teamRoleRepository);
        this.teamRepository = Objects.requireNonNull(teamRepository);
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.clock = Objects.requireNonNull(clock);
    }

    public CreateTeamRoleResult create(CreateTeamRoleCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        var team = teamRepository.findById(command.tenantId(), command.teamId())
                .orElseThrow(() -> new IllegalArgumentException("team not found"));
        if (team.status() != TeamStatus.ACTIVE) {
            throw new IllegalStateException("team is not active");
        }
        if (teamRoleRepository.findByCode(command.tenantId(), command.teamId(), command.roleCode())
                .isPresent()) {
            throw new IllegalStateException("team role code already exists");
        }

        Instant now = clock.instant();
        long teamRoleId = idGenerator.nextId();
        TeamRole teamRole = new TeamRole(
                teamRoleId,
                command.tenantId(),
                command.teamId(),
                command.roleCode(),
                command.roleName(),
                command.description(),
                TeamRoleStatus.ACTIVE,
                0,
                now,
                now);
        teamRoleRepository.save(teamRole);
        return new CreateTeamRoleResult(teamRoleId, TeamRoleStatus.ACTIVE, 0);
    }
}
