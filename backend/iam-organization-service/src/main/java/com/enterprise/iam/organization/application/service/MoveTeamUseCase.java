package com.enterprise.iam.organization.application.service;

import com.enterprise.iam.organization.application.command.MoveTeamCommand;
import com.enterprise.iam.organization.application.model.MoveTeamResult;
import com.enterprise.iam.organization.application.port.out.TeamRepository;
import com.enterprise.iam.organization.domain.model.Team;

import java.util.Objects;

public final class MoveTeamUseCase {

    static final int MAX_ANCESTOR_WALK = 32;

    private final TeamRepository teamRepository;

    public MoveTeamUseCase(TeamRepository teamRepository) {
        this.teamRepository = Objects.requireNonNull(teamRepository);
    }

    public MoveTeamResult move(MoveTeamCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Team team = teamRepository.findById(command.tenantId(), command.teamId())
                .orElseThrow(() -> new IllegalArgumentException("team not found"));

        Long newParentTeamId = command.newParentTeamId();
        if (newParentTeamId != null) {
            if (newParentTeamId == command.teamId()) {
                throw new IllegalStateException("move would create a cycle");
            }
            long cursor = newParentTeamId;
            for (int i = 0; i < MAX_ANCESTOR_WALK; i++) {
                if (cursor == command.teamId()) {
                    throw new IllegalStateException("move would create a cycle");
                }
                Team ancestor = teamRepository.findById(command.tenantId(), cursor)
                        .orElseThrow(() -> new IllegalArgumentException("parent team not found"));
                Long next = ancestor.parentTeamId();
                if (next == null) {
                    break;
                }
                cursor = next;
            }
        }

        teamRepository.moveTeam(
                command.tenantId(),
                command.teamId(),
                newParentTeamId,
                command.version());

        Team reloaded = teamRepository.findById(command.tenantId(), command.teamId())
                .orElseThrow(() -> new IllegalStateException("team not found after move"));
        return new MoveTeamResult(reloaded.id(), reloaded.status(), reloaded.version());
    }
}
