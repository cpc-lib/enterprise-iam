package com.enterprise.iam.file.application.command;

import java.util.Objects;

public record DeleteFileCommand(
        long tenantId,
        long fileId,
        long version,
        String reason,
        String traceId) {

    public DeleteFileCommand {
        if (tenantId <= 0 || fileId <= 0) {
            throw new IllegalArgumentException("tenantId and fileId must be positive");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must be non-negative");
        }
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(traceId, "traceId must not be null");
    }
}
