package com.enterprise.iam.organization.infrastructure.persistence;

import com.enterprise.iam.organization.application.port.out.TeamMemberRepository;
import com.enterprise.iam.organization.domain.model.TeamMember;
import com.enterprise.iam.organization.domain.model.TeamMemberStatus;
import com.enterprise.iam.organization.infrastructure.persistence.mapper.OrganizationPersistenceMapper;
import com.enterprise.iam.organization.infrastructure.persistence.mapper.OrganizationPersistenceMapper.TeamMemberRow;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public class JdbcTeamMemberRepository implements TeamMemberRepository {

    private final OrganizationPersistenceMapper mapper;

    public JdbcTeamMemberRepository(OrganizationPersistenceMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public TeamMember save(TeamMember teamMember) {
        int updated = mapper.insertTeamMember(
                teamMember.id(),
                teamMember.tenantId(),
                teamMember.teamId(),
                teamMember.userId(),
                teamMember.joinedAt(),
                Instant.now());
        if (updated != 1) {
            throw new IllegalStateException("team_member insert did not affect exactly one row");
        }
        return teamMember;
    }

    @Override
    public Optional<TeamMember> findActive(long tenantId, long teamId, long userId) {
        return Optional.ofNullable(mapper.findActiveTeamMember(tenantId, teamId, userId))
                .map(JdbcTeamMemberRepository::toDomain);
    }

    @Override
    public void removeMember(long tenantId, long teamId, long userId, long expectedVersion) {
        int updated = mapper.removeTeamMember(tenantId, teamId, userId, expectedVersion);
        if (updated != 1) {
            throw new IllegalStateException("team member remove conflict");
        }
        mapper.revokeTeamMemberRoles(tenantId, teamId, userId);
    }

    private static TeamMember toDomain(TeamMemberRow row) {
        return new TeamMember(
                row.id(),
                row.tenantId(),
                row.teamId(),
                row.userId(),
                TeamMemberStatus.valueOf(row.membershipStatus()),
                row.joinedAt(),
                row.leftAt(),
                row.version());
    }
}
