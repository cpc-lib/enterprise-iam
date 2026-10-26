package com.enterprise.iam.organization.application.command;

public record AddTeamMemberCommand(
        long tenantId,
        long teamId,
        long userId,
        String requestId) {

    public AddTeamMemberCommand {
        if (tenantId <= 0 || teamId <= 0 || userId <= 0) {
            throw new IllegalArgumentException("ids must be positive");
        }
    }
}
