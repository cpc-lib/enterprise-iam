package com.enterprise.iam.identity.application.command;

import java.util.Objects;

public record CreateRoleCommand(
        long tenantId,
        String roleCode,
        String roleName,
        String description,
        String requestId) {

    public CreateRoleCommand {
        if (tenantId <= 0) {
            throw new IllegalArgumentException("tenantId must be positive");
        }
        Objects.requireNonNull(roleCode, "roleCode must not be null");
        Objects.requireNonNull(roleName, "roleName must not be null");
        Objects.requireNonNull(requestId, "requestId must not be null");
    }
}
