package com.enterprise.iam.file.application.model;

/**
 * Metadata handed to a {@code FileScanPort} for one scan. The scanner never receives
 * object storage credentials or a stream in V1 — the local deterministic adapter only
 * inspects metadata.
 */
public record FileScanTarget(
        long fileId,
        long tenantId,
        String fileName,
        String declaredContentType,
        long fileSize,
        String sha256Hex) {
}
