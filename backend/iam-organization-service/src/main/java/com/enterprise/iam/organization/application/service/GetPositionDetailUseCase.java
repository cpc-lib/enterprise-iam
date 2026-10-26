package com.enterprise.iam.organization.application.service;

import com.enterprise.iam.organization.application.port.out.PositionRepository;
import com.enterprise.iam.organization.domain.model.Position;

import java.util.Objects;
import java.util.Optional;

public class GetPositionDetailUseCase {

    private final PositionRepository positionRepository;

    public GetPositionDetailUseCase(PositionRepository positionRepository) {
        this.positionRepository = Objects.requireNonNull(positionRepository);
    }

    public Optional<Position> getDetail(long tenantId, long positionId) {
        return positionRepository.findById(tenantId, positionId);
    }
}
