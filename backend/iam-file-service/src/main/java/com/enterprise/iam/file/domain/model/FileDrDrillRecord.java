package com.enterprise.iam.file.domain.model;

import java.time.Instant;
import java.util.Objects;

public record FileDrDrillRecord(
        long id,
        long tenantId,
        String scenarioCode,
        int targetRpoSeconds,
        int actualRpoSeconds,
        int targetRtoSeconds,
        int actualRtoSeconds,
        int verifiedObjects,
        String status,
        Instant executedAt) {

    public FileDrDrillRecord {
        Objects.requireNonNull(scenarioCode, "scenarioCode must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(executedAt, "executedAt must not be null");
    }
}
