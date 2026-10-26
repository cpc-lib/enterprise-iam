package com.enterprise.iam.identity.application.command;

import java.util.Objects;

public record EnableUserCommand(
        long tenantId,
        long userId,
        long expectedVersion,
        String reason,
        String requestId) {

    public EnableUserCommand {
        if (tenantId <= 0 || userId <= 0) {
            throw new IllegalArgumentException("tenantId and userId must be positive");
        }
        Objects.requireNonNull(requestId, "requestId must not be null");
    }
}
