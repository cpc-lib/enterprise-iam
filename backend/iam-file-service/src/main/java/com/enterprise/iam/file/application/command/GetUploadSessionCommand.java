package com.enterprise.iam.file.application.command;

import java.util.Objects;

public record GetUploadSessionCommand(
        long tenantId,
        long uploadId,
        String traceId) {

    public GetUploadSessionCommand {
        if (tenantId <= 0 || uploadId <= 0) {
            throw new IllegalArgumentException("tenantId and uploadId must be positive");
        }
        Objects.requireNonNull(traceId, "traceId must not be null");
    }
}
