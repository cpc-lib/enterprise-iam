package com.enterprise.iam.audit.domain.model;

import java.time.Instant;
import java.util.Objects;

public record AuditArchiveExportSummary(
        long tenantId,
        long exportedCount,
        String storageTarget,
        String olapEngine,
        Instant exportedAt) {

    public AuditArchiveExportSummary {
        Objects.requireNonNull(storageTarget, "storageTarget must not be null");
        Objects.requireNonNull(olapEngine, "olapEngine must not be null");
        Objects.requireNonNull(exportedAt, "exportedAt must not be null");
    }
}
