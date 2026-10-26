package com.enterprise.iam.file.interfaces.rest.dto;

import com.enterprise.iam.file.domain.model.FileAccessMode;

import java.time.Instant;

public record FileAccessResponse(
        FileAccessMode mode,
        String accessUrl,
        Instant expiresAt,
        long auditId) {
}
