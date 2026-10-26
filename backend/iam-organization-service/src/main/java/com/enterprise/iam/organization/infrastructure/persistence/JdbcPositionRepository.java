package com.enterprise.iam.organization.infrastructure.persistence;

import com.enterprise.iam.organization.application.port.out.PositionRepository;
import com.enterprise.iam.organization.domain.model.Position;
import com.enterprise.iam.organization.domain.model.PositionStatus;
import com.enterprise.iam.organization.infrastructure.persistence.mapper.OrganizationPersistenceMapper;
import com.enterprise.iam.organization.infrastructure.persistence.mapper.OrganizationPersistenceMapper.PositionRow;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class JdbcPositionRepository implements PositionRepository {

    private final OrganizationPersistenceMapper mapper;

    public JdbcPositionRepository(OrganizationPersistenceMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Position save(Position position) {
        int updated = mapper.insertPosition(
                position.id(),
                position.tenantId(),
                position.positionCode(),
                position.positionName(),
                position.organizationId(),
                position.sortOrder(),
                position.description(),
                position.status().name(),
                position.version(),
                Instant.now());
        if (updated != 1) {
            throw new IllegalStateException("position insert did not affect exactly one row");
        }
        return position;
    }

    @Override
    public Optional<Position> findById(long tenantId, long positionId) {
        return Optional.ofNullable(mapper.findPositionById(tenantId, positionId))
                .map(JdbcPositionRepository::toDomain);
    }

    @Override
    public Optional<Position> findByCode(long tenantId, String positionCode) {
        return Optional.ofNullable(mapper.findPositionByCode(tenantId, positionCode))
                .map(JdbcPositionRepository::toDomain);
    }

    @Override
    public List<Position> listPositions(long tenantId, Long organizationId, PositionStatus status, String keyword) {
        String statusStr = status == null ? null : status.name();
        return mapper.listPositions(tenantId, organizationId, statusStr, keyword).stream()
                .map(JdbcPositionRepository::toDomain)
                .toList();
    }

    @Override
    public boolean update(Position position, long expectedVersion) {
        int updated = mapper.updatePosition(
                position.id(),
                position.tenantId(),
                position.positionCode(),
                position.positionName(),
                position.organizationId(),
                position.sortOrder(),
                position.description(),
                expectedVersion,
                Instant.now());
        return updated == 1;
    }

    @Override
    public boolean updateStatus(long tenantId, long positionId, PositionStatus status, long expectedVersion) {
        int updated = mapper.updatePositionStatus(
                positionId,
                tenantId,
                status.name(),
                expectedVersion,
                Instant.now());
        return updated == 1;
    }

    @Override
    public boolean delete(long tenantId, long positionId, long expectedVersion) {
        int updated = mapper.softDeletePosition(
                positionId,
                tenantId,
                expectedVersion,
                Instant.now());
        return updated == 1;
    }

    private static Position toDomain(PositionRow row) {
        return new Position(
                row.id(),
                row.tenantId(),
                row.positionCode(),
                row.positionName(),
                row.organizationId(),
                row.sortOrder(),
                row.description(),
                PositionStatus.valueOf(row.status()),
                row.version(),
                row.createdAt(),
                row.updatedAt());
    }
}
