package com.enterprise.iam.file.application.model;

import com.enterprise.iam.file.domain.model.UploadMode;
import com.enterprise.iam.file.domain.model.UploadSessionStatus;

import java.time.Instant;
import java.util.List;

public record UploadSessionResult(
        UploadMode mode,
        Long uploadId,
        Long fileId,
        UploadSessionStatus status,
        Long partSize,
        Integer partCount,
        List<Integer> uploadedParts,
        Instant expiresAt,
        long version) {
}
