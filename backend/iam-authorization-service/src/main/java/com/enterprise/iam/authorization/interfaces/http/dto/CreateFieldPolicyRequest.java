package com.enterprise.iam.authorization.interfaces.http.dto;

import jakarta.validation.constraints.NotNull;

public record CreateFieldPolicyRequest(
        @NotNull Long resourceId,
        @NotNull Long operationId,
        @NotNull Long fieldId,
        boolean readable,
        boolean writable,
        boolean hidden,
        Long maskStrategyId,
        int priority) {
}
