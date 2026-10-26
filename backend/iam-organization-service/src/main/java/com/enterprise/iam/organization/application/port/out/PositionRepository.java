package com.enterprise.iam.organization.application.port.out;

import com.enterprise.iam.organization.domain.model.Position;
import com.enterprise.iam.organization.domain.model.PositionStatus;

import java.util.List;
import java.util.Optional;

public interface PositionRepository {

    Position save(Position position);

    Optional<Position> findById(long tenantId, long positionId);

    Optional<Position> findByCode(long tenantId, String positionCode);

    List<Position> listPositions(long tenantId, Long organizationId, PositionStatus status, String keyword);

    boolean update(Position position, long expectedVersion);

    boolean updateStatus(long tenantId, long positionId, PositionStatus status, long expectedVersion);

    boolean delete(long tenantId, long positionId, long expectedVersion);
}
