package com.enterprise.iam.sharing.application.command;

import java.util.Objects;

public record RevokeShareCommand(
        long tenantId,
        long shareId,
        String reason,
        long version,
        String requestId) {

    public RevokeShareCommand {
        if (tenantId <= 0 || shareId <= 0) {
            throw new IllegalArgumentException("tenantId and shareId must be positive");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(requestId, "requestId must not be null");
    }
}
