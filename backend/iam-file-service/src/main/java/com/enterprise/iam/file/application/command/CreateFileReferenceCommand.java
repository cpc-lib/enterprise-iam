package com.enterprise.iam.file.application.command;

import com.enterprise.iam.file.domain.model.FileReferenceType;

import java.util.Objects;

public record CreateFileReferenceCommand(
        long tenantId,
        long fileId,
        long resourceId,
        String resourceInstanceKey,
        FileReferenceType referenceType,
        Long fieldId,
        int sortOrder,
        String traceId) {

    public CreateFileReferenceCommand {
        if (tenantId <= 0 || fileId <= 0 || resourceId <= 0) {
            throw new IllegalArgumentException("tenantId, fileId and resourceId must be positive");
        }
        if (sortOrder < 0 || sortOrder > 100000) {
            throw new IllegalArgumentException("sortOrder must be 0..100000");
        }
        Objects.requireNonNull(resourceInstanceKey, "resourceInstanceKey must not be null");
        Objects.requireNonNull(referenceType, "referenceType must not be null");
        Objects.requireNonNull(traceId, "traceId must not be null");
    }
}
