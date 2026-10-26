package com.enterprise.iam.authorization.interfaces.http.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record AuthorizationCheckRequest(
        @NotNull Long tenantId,
        @NotNull Long subjectId,
        @NotNull Long sessionId,
        @NotNull Long resourceId,
        @NotNull Long operationId,
        String resourceInstanceId,
        @NotNull Long permissionVersion,
        @NotNull @Size(min = 8, max = 128) String requestId,
        Map<String, Object> context) {
}
