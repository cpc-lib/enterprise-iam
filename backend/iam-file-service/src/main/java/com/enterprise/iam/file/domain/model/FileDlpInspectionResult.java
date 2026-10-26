package com.enterprise.iam.file.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record FileDlpInspectionResult(
        long fileId,
        long tenantId,
        FileDlpStatus dlpStatus,
        boolean archiveBombDetected,
        double compressionRatio,
        int nestedArchiveDepth,
        List<String> matchedPatterns,
        FileState fileStatus,
        Instant inspectedAt) {

    public FileDlpInspectionResult {
        Objects.requireNonNull(dlpStatus, "dlpStatus must not be null");
        matchedPatterns = matchedPatterns == null ? List.of() : List.copyOf(matchedPatterns);
        Objects.requireNonNull(fileStatus, "fileStatus must not be null");
        Objects.requireNonNull(inspectedAt, "inspectedAt must not be null");
    }
}
