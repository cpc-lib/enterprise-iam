package com.enterprise.iam.file.application.model;

import com.enterprise.iam.file.domain.model.FileAccessMode;

import java.time.Instant;

public record FileAccessResult(
        FileAccessMode mode,
        String accessUrl,
        Instant expiresAt,
        long auditId) {
}
