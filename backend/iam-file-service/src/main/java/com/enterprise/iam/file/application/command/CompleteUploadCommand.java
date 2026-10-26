package com.enterprise.iam.file.application.command;

import java.util.List;
import java.util.Objects;

public record CompleteUploadCommand(
        long tenantId,
        long uploadId,
        long version,
        String wholeSha256Hex,
        List<PartEtag> parts,
        String traceId) {

    public CompleteUploadCommand {
        if (tenantId <= 0 || uploadId <= 0) {
            throw new IllegalArgumentException("tenantId and uploadId must be positive");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must be non-negative");
        }
        Objects.requireNonNull(wholeSha256Hex, "wholeSha256Hex must not be null");
        Objects.requireNonNull(parts, "parts must not be null");
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("parts must not be empty");
        }
        Objects.requireNonNull(traceId, "traceId must not be null");
    }

    public record PartEtag(int partNumber, String storageEtag) {
        public PartEtag {
            if (partNumber < 1 || partNumber > 10000) {
                throw new IllegalArgumentException("partNumber must be 1..10000");
            }
            Objects.requireNonNull(storageEtag, "storageEtag must not be null");
        }
    }
}
