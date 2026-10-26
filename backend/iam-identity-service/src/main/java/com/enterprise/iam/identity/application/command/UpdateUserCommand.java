package com.enterprise.iam.identity.application.command;

import java.util.Objects;

public record UpdateUserCommand(
        long tenantId,
        long userId,
        String displayName,
        String email,
        String phone,
        Long departmentId,
        Long positionId,
        long expectedVersion,
        String requestId) {

    public UpdateUserCommand {
        if (tenantId <= 0 || userId <= 0) {
            throw new IllegalArgumentException("tenantId and userId must be positive");
        }
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(requestId, "requestId must not be null");
    }
}
