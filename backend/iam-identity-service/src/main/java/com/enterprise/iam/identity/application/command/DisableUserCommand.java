package com.enterprise.iam.identity.application.command;

import java.util.Objects;

public record DisableUserCommand(
        long tenantId,
        long userId,
        long version,
        String reason,
        String requestId) {

    public DisableUserCommand {
        if (tenantId <= 0 || userId <= 0) {
            throw new IllegalArgumentException("tenantId and userId must be positive");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(requestId, "requestId must not be null");
    }
}
