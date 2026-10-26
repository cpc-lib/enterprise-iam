package com.enterprise.iam.file.application.command;

import java.util.Objects;

public record AbortUploadCommand(
        long tenantId,
        long uploadId,
        long version,
        String traceId) {

    public AbortUploadCommand {
        if (tenantId <= 0 || uploadId <= 0) {
            throw new IllegalArgumentException("tenantId and uploadId must be positive");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must be non-negative");
        }
        Objects.requireNonNull(traceId, "traceId must not be null");
    }
}
