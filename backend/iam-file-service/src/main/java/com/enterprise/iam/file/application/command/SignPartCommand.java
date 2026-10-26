package com.enterprise.iam.file.application.command;

import java.util.Objects;

public record SignPartCommand(
        long tenantId,
        long uploadId,
        int partNumber,
        long version,
        long expectedSize,
        String partSha256Hex,
        String traceId) {

    public SignPartCommand {
        if (tenantId <= 0 || uploadId <= 0) {
            throw new IllegalArgumentException("tenantId and uploadId must be positive");
        }
        if (partNumber < 1 || partNumber > 10000) {
            throw new IllegalArgumentException("partNumber must be 1..10000");
        }
        if (expectedSize <= 0) {
            throw new IllegalArgumentException("expectedSize must be positive");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must be non-negative");
        }
        Objects.requireNonNull(traceId, "traceId must not be null");
    }
}
