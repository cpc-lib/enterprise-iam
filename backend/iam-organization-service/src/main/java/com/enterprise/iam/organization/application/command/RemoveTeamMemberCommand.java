package com.enterprise.iam.organization.application.command;

import java.util.Objects;

public record RemoveTeamMemberCommand(
        long tenantId,
        long teamId,
        long userId,
        long version,
        String requestId) {

    public RemoveTeamMemberCommand {
        if (tenantId <= 0 || teamId <= 0 || userId <= 0) {
            throw new IllegalArgumentException("ids must be positive");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        Objects.requireNonNull(requestId, "requestId must not be null");
    }
}
