package com.enterprise.iam.organization.application.command;

import java.util.Objects;

public record CreateTeamRoleCommand(
        long tenantId,
        long teamId,
        String roleCode,
        String roleName,
        String description,
        String requestId) {

    public CreateTeamRoleCommand {
        if (tenantId <= 0 || teamId <= 0) {
            throw new IllegalArgumentException("tenantId and teamId must be positive");
        }
        Objects.requireNonNull(roleCode, "roleCode must not be null");
        Objects.requireNonNull(roleName, "roleName must not be null");
        Objects.requireNonNull(requestId, "requestId must not be null");
    }
}
