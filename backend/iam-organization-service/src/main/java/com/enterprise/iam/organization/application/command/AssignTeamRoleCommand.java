package com.enterprise.iam.organization.application.command;

public record AssignTeamRoleCommand(
        long tenantId,
        long teamId,
        long userId,
        long teamRoleId,
        String requestId) {

    public AssignTeamRoleCommand {
        if (tenantId <= 0 || teamId <= 0 || userId <= 0 || teamRoleId <= 0) {
            throw new IllegalArgumentException("ids must be positive");
        }
    }
}
