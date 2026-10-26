package com.enterprise.iam.audit.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RecordAuditLogRequest(
        @NotNull @Positive Long tenantId,
        @NotNull @Positive Long actorId,
        @NotBlank String action,
        @NotBlank String resourceType,
        @NotNull @Positive Long resourceId,
        String detail) {
}
