package com.enterprise.iam.organization.application.service;

import com.enterprise.iam.organization.application.command.AddTeamMemberCommand;
import com.enterprise.iam.organization.application.model.AddTeamMemberResult;
import com.enterprise.iam.organization.application.port.out.PositiveIdGenerator;
import com.enterprise.iam.organization.application.port.out.TeamMemberRepository;
import com.enterprise.iam.organization.application.port.out.TeamRepository;
import com.enterprise.iam.organization.domain.model.TeamMember;
import com.enterprise.iam.organization.domain.model.TeamMemberStatus;
import com.enterprise.iam.organization.domain.model.TeamStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class AddTeamMemberUseCase {

    private final TeamMemberRepository teamMemberRepository;
    private final TeamRepository teamRepository;
    private final PositiveIdGenerator idGenerator;
    private final Clock clock;

    public AddTeamMemberUseCase(
            TeamMemberRepository teamMemberRepository,
            TeamRepository teamRepository,
            PositiveIdGenerator idGenerator,
            Clock clock) {
        this.teamMemberRepository = Objects.requireNonNull(teamMemberRepository);
        this.teamRepository = Objects.requireNonNull(teamRepository);
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.clock = Objects.requireNonNull(clock);
    }

    public AddTeamMemberResult add(AddTeamMemberCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        var team = teamRepository.findById(command.tenantId(), command.teamId())
                .orElseThrow(() -> new IllegalArgumentException("team not found"));
        if (team.status() != TeamStatus.ACTIVE) {
            throw new IllegalStateException("team is not active");
        }

        var existing = teamMemberRepository.findActive(
                command.tenantId(), command.teamId(), command.userId());
        if (existing.isPresent()) {
            return new AddTeamMemberResult(
                    existing.get().id(), existing.get().membershipStatus(), existing.get().version());
        }

        Instant now = clock.instant();
        long teamMemberId = idGenerator.nextId();
        TeamMember member = new TeamMember(
                teamMemberId,
                command.tenantId(),
                command.teamId(),
                command.userId(),
                TeamMemberStatus.ACTIVE,
                now,
                null,
                0);
        teamMemberRepository.save(member);
        return new AddTeamMemberResult(teamMemberId, TeamMemberStatus.ACTIVE, 0);
    }
}
