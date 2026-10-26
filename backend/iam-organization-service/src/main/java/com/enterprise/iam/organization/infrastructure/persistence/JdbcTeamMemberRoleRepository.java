package com.enterprise.iam.organization.infrastructure.persistence;

import com.enterprise.iam.organization.application.port.out.TeamMemberRoleRepository;
import com.enterprise.iam.organization.domain.model.TeamMemberRole;
import com.enterprise.iam.organization.domain.model.TeamMemberRoleStatus;
import com.enterprise.iam.organization.infrastructure.persistence.mapper.OrganizationPersistenceMapper;
import com.enterprise.iam.organization.infrastructure.persistence.mapper.OrganizationPersistenceMapper.TeamMemberRoleRow;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public class JdbcTeamMemberRoleRepository implements TeamMemberRoleRepository {

    private final OrganizationPersistenceMapper mapper;

    public JdbcTeamMemberRoleRepository(OrganizationPersistenceMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public TeamMemberRole save(TeamMemberRole teamMemberRole) {
        int updated = mapper.insertTeamMemberRole(
                teamMemberRole.id(),
                teamMemberRole.tenantId(),
                teamMemberRole.teamId(),
                teamMemberRole.userId(),
                teamMemberRole.teamRoleId(),
                teamMemberRole.startTime(),
                Instant.now());
        if (updated != 1) {
            throw new IllegalStateException("team_member_role insert did not affect exactly one row");
        }
        return teamMemberRole;
    }

    @Override
    public Optional<TeamMemberRole> findActive(long tenantId, long teamId, long userId, long teamRoleId) {
        return Optional.ofNullable(mapper.findActiveTeamMemberRole(tenantId, teamId, userId, teamRoleId))
                .map(JdbcTeamMemberRoleRepository::toDomain);
    }

    private static TeamMemberRole toDomain(TeamMemberRoleRow row) {
        return new TeamMemberRole(
                row.id(),
                row.tenantId(),
                row.teamId(),
                row.userId(),
                row.teamRoleId(),
                TeamMemberRoleStatus.valueOf(row.status()),
                row.startTime(),
                row.expireTime(),
                row.version());
    }
}
