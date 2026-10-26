package com.enterprise.iam.organization.application.port.out;

import com.enterprise.iam.organization.domain.model.TeamRole;

import java.util.Optional;

public interface TeamRoleRepository {

    TeamRole save(TeamRole teamRole);

    Optional<TeamRole> findById(long tenantId, long teamRoleId);

    Optional<TeamRole> findByCode(long tenantId, long teamId, String roleCode);
}
