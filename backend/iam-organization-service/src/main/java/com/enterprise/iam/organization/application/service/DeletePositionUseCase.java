package com.enterprise.iam.organization.application.service;

import com.enterprise.iam.organization.application.port.out.PositionRepository;
import com.enterprise.iam.organization.domain.model.Position;

import java.util.Objects;

public class DeletePositionUseCase {

    private final PositionRepository positionRepository;

    public DeletePositionUseCase(PositionRepository positionRepository) {
        this.positionRepository = Objects.requireNonNull(positionRepository);
    }

    public void delete(long tenantId, long positionId, long expectedVersion) {
        Position existing = positionRepository.findById(tenantId, positionId)
                .orElseThrow(() -> new IllegalArgumentException("position not found"));

        if (existing.version() != expectedVersion) {
            throw new IllegalStateException("IAM_OPTIMISTIC_LOCK_ERROR: position version mismatch");
        }

        boolean ok = positionRepository.delete(tenantId, positionId, expectedVersion);
        if (!ok) {
            throw new IllegalStateException("failed to delete position");
        }
    }
}
