package com.enterprise.iam.organization.application.service;

import com.enterprise.iam.organization.application.port.out.PositionRepository;
import com.enterprise.iam.organization.domain.model.Position;
import com.enterprise.iam.organization.domain.model.PositionStatus;

import java.util.Objects;

public class EnablePositionUseCase {

    private final PositionRepository positionRepository;

    public EnablePositionUseCase(PositionRepository positionRepository) {
        this.positionRepository = Objects.requireNonNull(positionRepository);
    }

    public Position enable(long tenantId, long positionId, long expectedVersion) {
        Position existing = positionRepository.findById(tenantId, positionId)
                .orElseThrow(() -> new IllegalArgumentException("position not found"));

        if (existing.version() != expectedVersion) {
            throw new IllegalStateException("IAM_OPTIMISTIC_LOCK_ERROR: position version mismatch");
        }

        boolean ok = positionRepository.updateStatus(tenantId, positionId, PositionStatus.ACTIVE, expectedVersion);
        if (!ok) {
            throw new IllegalStateException("failed to enable position");
        }

        return positionRepository.findById(tenantId, positionId)
                .orElseThrow(() -> new IllegalStateException("enabled position could not be reloaded"));
    }
}
