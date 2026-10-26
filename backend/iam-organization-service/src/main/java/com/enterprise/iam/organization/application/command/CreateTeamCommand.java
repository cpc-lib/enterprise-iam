package com.enterprise.iam.organization.application.command;

import java.util.Objects;

public record CreateTeamCommand(
        long tenantId,
        long organizationId,
        Long parentTeamId,
        String teamCode,
        String teamName,
        String requestId) {

    public CreateTeamCommand {
        if (tenantId <= 0 || organizationId <= 0) {
            throw new IllegalArgumentException("tenantId and organizationId must be positive");
        }
        if (parentTeamId != null && parentTeamId <= 0) {
            throw new IllegalArgumentException("parentTeamId must be positive when present");
        }
        Objects.requireNonNull(teamCode, "teamCode must not be null");
        Objects.requireNonNull(teamName, "teamName must not be null");
        Objects.requireNonNull(requestId, "requestId must not be null");
    }
}
