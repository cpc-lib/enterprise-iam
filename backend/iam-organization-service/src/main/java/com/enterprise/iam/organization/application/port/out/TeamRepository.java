package com.enterprise.iam.organization.application.port.out;

import com.enterprise.iam.organization.domain.model.Team;

import java.util.Optional;

public interface TeamRepository {

    Team save(Team team);

    Optional<Team> findById(long tenantId, long teamId);

    Optional<Team> findByCode(long tenantId, String teamCode);

    void moveTeam(long tenantId, long teamId, Long newParentTeamId, long expectedVersion);
}
