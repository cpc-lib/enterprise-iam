package com.enterprise.iam.identity.application.command;

public record BindUserRoleCommand(
        long tenantId,
        long userId,
        long roleId,
        long version,
        String requestId) {

    public BindUserRoleCommand {
        if (tenantId <= 0 || userId <= 0 || roleId <= 0) {
            throw new IllegalArgumentException("ids must be positive");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }
}
