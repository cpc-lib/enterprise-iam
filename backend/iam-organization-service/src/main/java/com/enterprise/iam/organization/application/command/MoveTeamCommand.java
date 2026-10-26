package com.enterprise.iam.organization.application.command;

import java.util.Objects;

public record MoveTeamCommand(
        long tenantId,
        long teamId,
        Long newParentTeamId,
        long version,
        String requestId) {

    public MoveTeamCommand {
        if (tenantId <= 0 || teamId <= 0) {
            throw new IllegalArgumentException("tenantId and teamId must be positive");
        }
        if (newParentTeamId != null && newParentTeamId <= 0) {
            throw new IllegalArgumentException("newParentTeamId must be positive when present");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        Objects.requireNonNull(requestId, "requestId must not be null");
    }
}
