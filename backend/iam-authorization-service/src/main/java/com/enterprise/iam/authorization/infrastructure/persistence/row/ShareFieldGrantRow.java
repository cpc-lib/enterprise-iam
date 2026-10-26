package com.enterprise.iam.authorization.infrastructure.persistence.row;

public record ShareFieldGrantRow(
        long shareId,
        long fieldId,
        boolean readable,
        boolean writable,
        boolean hidden,
        Long maskStrategyId) {
}
