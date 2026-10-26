package com.enterprise.iam.identity.application.command;

import com.enterprise.iam.identity.domain.model.IdentityType;

import java.util.Objects;

public record CreateUserCommand(
        long tenantId,
        String displayName,
        IdentityType identityType,
        String identityValue,
        String initialPassword,
        Long departmentId,
        Long positionId,
        String email,
        String phone,
        String requestId) {

    public CreateUserCommand(
            long tenantId,
            String displayName,
            IdentityType identityType,
            String identityValue,
            String initialPassword,
            String requestId) {
        this(tenantId, displayName, identityType, identityValue, initialPassword, null, null, null, null, requestId);
    }

    public CreateUserCommand {
        if (tenantId <= 0) {
            throw new IllegalArgumentException("tenantId must be positive");
        }
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(identityType, "identityType must not be null");
        Objects.requireNonNull(identityValue, "identityValue must not be null");
        Objects.requireNonNull(requestId, "requestId must not be null");
    }
}
