package com.enterprise.iam.organization.application.service;

import com.enterprise.iam.organization.application.port.out.PositionRepository;
import com.enterprise.iam.organization.domain.model.Position;
import com.enterprise.iam.organization.domain.model.PositionStatus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ListPositionsUseCase {

    private final PositionRepository positionRepository;

    public ListPositionsUseCase(PositionRepository positionRepository) {
        this.positionRepository = Objects.requireNonNull(positionRepository);
    }

    public List<Position> listPositions(long tenantId, Long organizationId, PositionStatus status, String keyword) {
        return positionRepository.listPositions(tenantId, organizationId, status, keyword);
    }
}
