package com.enterprise.iam.organization.application.port.out;

import com.enterprise.iam.organization.domain.model.TeamMember;

import java.util.Optional;

public interface TeamMemberRepository {

    TeamMember save(TeamMember teamMember);

    Optional<TeamMember> findActive(long tenantId, long teamId, long userId);

    void removeMember(long tenantId, long teamId, long userId, long expectedVersion);
}
