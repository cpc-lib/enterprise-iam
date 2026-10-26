package com.enterprise.iam.organization.application.service;

import com.enterprise.iam.organization.application.command.AssignTeamRoleCommand;
import com.enterprise.iam.organization.application.model.AssignTeamRoleResult;
import com.enterprise.iam.organization.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.organization.application.port.out.TeamMemberRepository;
import com.enterprise.iam.organization.application.port.out.TeamMemberRoleRepository;
import com.enterprise.iam.organization.application.port.out.TeamRepository;
import com.enterprise.iam.organization.application.port.out.TeamRoleRepository;
import com.enterprise.iam.organization.domain.model.TeamMemberRole;
import com.enterprise.iam.organization.domain.model.TeamMemberRoleStatus;
import com.enterprise.iam.organization.domain.model.TeamMemberStatus;
import com.enterprise.iam.organization.domain.model.TeamRoleStatus;
import com.enterprise.iam.organization.domain.model.TeamStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class AssignTeamRoleUseCase {

    private final TeamMemberRoleRepository teamMemberRoleRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamRoleRepository teamRoleRepository;
    private final TeamRepository teamRepository;
    private final PositiveIdGenerator idGenerator;
    private final Clock clock;

    public AssignTeamRoleUseCase(
            TeamMemberRoleRepository teamMemberRoleRepository,
            TeamMemberRepository teamMemberRepository,
            TeamRoleRepository teamRoleRepository,
            TeamRepository teamRepository,
            PositiveIdGenerator idGenerator,
            Clock clock) {
        this.teamMemberRoleRepository = Objects.requireNonNull(teamMemberRoleRepository);
        this.teamMemberRepository = Objects.requireNonNull(teamMemberRepository);
        this.teamRoleRepository = Objects.requireNonNull(teamRoleRepository);
        this.teamRepository = Objects.requireNonNull(teamRepository);
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.clock = Objects.requireNonNull(clock);
    }

    public AssignTeamRoleResult assign(AssignTeamRoleCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        var team = teamRepository.findById(command.tenantId(), command.teamId())
                .orElseThrow(() -> new IllegalArgumentException("team not found"));
        if (team.status() != TeamStatus.ACTIVE) {
            throw new IllegalStateException("team is not active");
        }

        var member = teamMemberRepository.findActive(
                command.tenantId(), command.teamId(), command.userId())
                .orElseThrow(() -> new IllegalArgumentException("team member not found"));
        if (member.membershipStatus() != TeamMemberStatus.ACTIVE) {
            throw new IllegalStateException("team member is not active");
        }

        var role = teamRoleRepository.findById(command.tenantId(), command.teamRoleId())
                .orElseThrow(() -> new IllegalArgumentException("team role not found"));
        if (role.status() != TeamRoleStatus.ACTIVE) {
            throw new IllegalStateException("team role is not active");
        }

        var existing = teamMemberRoleRepository.findActive(
                command.tenantId(), command.teamId(), command.userId(), command.teamRoleId());
        if (existing.isPresent()) {
            return new AssignTeamRoleResult(
                    existing.get().id(), existing.get().status(), existing.get().version());
        }

        Instant now = clock.instant();
        long teamMemberRoleId = idGenerator.nextId();
        TeamMemberRole assignment = new TeamMemberRole(
                teamMemberRoleId,
                command.tenantId(),
                command.teamId(),
                command.userId(),
                command.teamRoleId(),
                TeamMemberRoleStatus.ACTIVE,
                now,
                null,
                0);
        teamMemberRoleRepository.save(assignment);
        return new AssignTeamRoleResult(teamMemberRoleId, TeamMemberRoleStatus.ACTIVE, 0);
    }
}
