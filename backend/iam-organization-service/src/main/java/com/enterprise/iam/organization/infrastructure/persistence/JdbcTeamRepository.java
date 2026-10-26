package com.enterprise.iam.organization.infrastructure.persistence;

import com.enterprise.iam.organization.application.port.out.TeamRepository;
import com.enterprise.iam.organization.domain.model.Team;
import com.enterprise.iam.organization.domain.model.TeamStatus;
import com.enterprise.iam.organization.infrastructure.persistence.mapper.OrganizationPersistenceMapper;
import com.enterprise.iam.organization.infrastructure.persistence.mapper.OrganizationPersistenceMapper.TeamRow;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public class JdbcTeamRepository implements TeamRepository {

    private final OrganizationPersistenceMapper mapper;

    public JdbcTeamRepository(OrganizationPersistenceMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Team save(Team team) {
        int updated = mapper.insertTeam(
                team.id(),
                team.tenantId(),
                team.organizationId(),
                team.parentTeamId(),
                team.teamCode(),
                team.teamName(),
                team.materializedPath(),
                team.depth(),
                team.status().name(),
                team.version(),
                Instant.now());
        if (updated != 1) {
            throw new IllegalStateException("team insert did not affect exactly one row");
        }
        return team;
    }

    @Override
    public Optional<Team> findById(long tenantId, long teamId) {
        return Optional.ofNullable(mapper.findTeamById(tenantId, teamId))
                .map(JdbcTeamRepository::toDomain);
    }

    @Override
    public Optional<Team> findByCode(long tenantId, String teamCode) {
        return Optional.ofNullable(mapper.findTeamByCode(tenantId, teamCode))
                .map(JdbcTeamRepository::toDomain);
    }

    @Override
    public void moveTeam(long tenantId, long teamId, Long newParentTeamId, long expectedVersion) {
        int updated = mapper.moveTeam(tenantId, teamId, newParentTeamId, expectedVersion);
        if (updated != 1) {
            throw new IllegalStateException("team move conflict");
        }
    }

    private static Team toDomain(TeamRow row) {
        return new Team(
                row.id(),
                row.tenantId(),
                row.organizationId(),
                row.parentTeamId(),
                row.teamCode(),
                row.teamName(),
                row.materializedPath(),
                row.depth(),
                TeamStatus.valueOf(row.status()),
                row.version(),
                row.createdAt(),
                row.updatedAt());
    }
}
