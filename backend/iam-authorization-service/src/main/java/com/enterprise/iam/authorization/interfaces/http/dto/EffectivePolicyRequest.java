package com.enterprise.iam.authorization.interfaces.http.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EffectivePolicyRequest(
        @NotNull Long tenantId,
        @NotNull Long subjectId,
        @NotNull Long resourceId,
        @NotNull Long operationId,
        long permissionVersion,
        @NotNull @Size(min = 8, max = 128) String requestId) {
}
