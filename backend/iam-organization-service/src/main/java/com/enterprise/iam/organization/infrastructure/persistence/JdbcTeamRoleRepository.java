package com.enterprise.iam.organization.infrastructure.persistence;

import com.enterprise.iam.organization.application.port.out.TeamRoleRepository;
import com.enterprise.iam.organization.domain.model.TeamRole;
import com.enterprise.iam.organization.domain.model.TeamRoleStatus;
import com.enterprise.iam.organization.infrastructure.persistence.mapper.OrganizationPersistenceMapper;
import com.enterprise.iam.organization.infrastructure.persistence.mapper.OrganizationPersistenceMapper.TeamRoleRow;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public class JdbcTeamRoleRepository implements TeamRoleRepository {

    private final OrganizationPersistenceMapper mapper;

    public JdbcTeamRoleRepository(OrganizationPersistenceMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public TeamRole save(TeamRole teamRole) {
        int updated = mapper.insertTeamRole(
                teamRole.id(),
                teamRole.tenantId(),
                teamRole.teamId(),
                teamRole.roleCode(),
                teamRole.roleName(),
                teamRole.description(),
                teamRole.status().name(),
                Instant.now());
        if (updated != 1) {
            throw new IllegalStateException("team_role insert did not affect exactly one row");
        }
        return teamRole;
    }

    @Override
    public Optional<TeamRole> findById(long tenantId, long teamRoleId) {
        return Optional.ofNullable(mapper.findTeamRoleById(tenantId, teamRoleId))
                .map(JdbcTeamRoleRepository::toDomain);
    }

    @Override
    public Optional<TeamRole> findByCode(long tenantId, long teamId, String roleCode) {
        return Optional.ofNullable(mapper.findTeamRoleByCode(tenantId, teamId, roleCode))
                .map(JdbcTeamRoleRepository::toDomain);
    }

    private static TeamRole toDomain(TeamRoleRow row) {
        return new TeamRole(
                row.id(),
                row.tenantId(),
                row.teamId(),
                row.roleCode(),
                row.roleName(),
                row.description(),
                TeamRoleStatus.valueOf(row.status()),
                row.version(),
                row.createdAt(),
                row.updatedAt());
    }
}
