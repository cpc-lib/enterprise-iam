package com.enterprise.iam.organization.application.service;

import com.enterprise.iam.organization.application.command.RemoveTeamMemberCommand;
import com.enterprise.iam.organization.application.port.out.TeamMemberRepository;

import java.util.Objects;

public final class RemoveTeamMemberUseCase {

    private final TeamMemberRepository teamMemberRepository;

    public RemoveTeamMemberUseCase(TeamMemberRepository teamMemberRepository) {
        this.teamMemberRepository = Objects.requireNonNull(teamMemberRepository);
    }

    public void remove(RemoveTeamMemberCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        teamMemberRepository.removeMember(
                command.tenantId(),
                command.teamId(),
                command.userId(),
                command.version());
    }
}
