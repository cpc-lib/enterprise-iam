package com.enterprise.iam.file.application.command;

import com.enterprise.iam.file.domain.model.FileAccessMode;

import java.util.Objects;

public record FileAccessCommand(
        long tenantId,
        long fileId,
        long resourceId,
        String resourceInstanceKey,
        Long referenceId,
        FileAccessMode requestedMode,
        boolean preview,
        String traceId) {

    public FileAccessCommand {
        if (tenantId <= 0 || fileId <= 0 || resourceId <= 0) {
            throw new IllegalArgumentException("tenantId, fileId and resourceId must be positive");
        }
        Objects.requireNonNull(resourceInstanceKey, "resourceInstanceKey must not be null");
        Objects.requireNonNull(traceId, "traceId must not be null");
    }
}
