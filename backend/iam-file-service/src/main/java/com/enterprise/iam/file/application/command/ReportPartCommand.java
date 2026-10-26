package com.enterprise.iam.file.application.command;

import java.util.Objects;

public record ReportPartCommand(
        long tenantId,
        long uploadId,
        int partNumber,
        long version,
        String storageEtag,
        long partSize,
        String partSha256Hex,
        String traceId) {

    public ReportPartCommand {
        if (tenantId <= 0 || uploadId <= 0) {
            throw new IllegalArgumentException("tenantId and uploadId must be positive");
        }
        if (partNumber < 1 || partNumber > 10000) {
            throw new IllegalArgumentException("partNumber must be 1..10000");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must be non-negative");
        }
        if (partSize <= 0) {
            throw new IllegalArgumentException("partSize must be positive");
        }
        Objects.requireNonNull(storageEtag, "storageEtag must not be null");
        Objects.requireNonNull(traceId, "traceId must not be null");
    }
}
