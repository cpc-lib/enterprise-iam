package com.enterprise.iam.file.application.command;

import java.util.Objects;

public record CreateUploadSessionCommand(
        long tenantId,
        long userId,
        String fileName,
        long fileSize,
        String sha256Hex,
        String declaredContentType,
        long resourceId,
        String resourceInstanceKey,
        Long fieldId,
        String traceId) {

    public CreateUploadSessionCommand {
        if (tenantId <= 0 || userId <= 0 || resourceId <= 0) {
            throw new IllegalArgumentException("tenantId, userId and resourceId must be positive");
        }
        if (fileSize <= 0 || fileSize > 53687091200L) {
            throw new IllegalArgumentException("fileSize must be 1..53687091200");
        }
        Objects.requireNonNull(fileName, "fileName must not be null");
        Objects.requireNonNull(sha256Hex, "sha256Hex must not be null");
        Objects.requireNonNull(declaredContentType, "declaredContentType must not be null");
        Objects.requireNonNull(resourceInstanceKey, "resourceInstanceKey must not be null");
        Objects.requireNonNull(traceId, "traceId must not be null");
    }
}
