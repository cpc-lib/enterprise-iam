package com.enterprise.iam.organization.application.port.out;

import com.enterprise.iam.organization.domain.model.TeamMemberRole;

import java.util.Optional;

public interface TeamMemberRoleRepository {

    TeamMemberRole save(TeamMemberRole teamMemberRole);

    Optional<TeamMemberRole> findActive(long tenantId, long teamId, long userId, long teamRoleId);
}
