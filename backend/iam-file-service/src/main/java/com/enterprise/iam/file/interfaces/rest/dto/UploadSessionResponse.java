package com.enterprise.iam.file.interfaces.rest.dto;

import com.enterprise.iam.common.web.json.NumericLong;
import com.enterprise.iam.file.domain.model.UploadMode;
import com.enterprise.iam.file.domain.model.UploadSessionStatus;

import java.time.Instant;
import java.util.List;

/**
 * Frozen OpenAPI {@code UploadSessionResponse}. {@code uploadId}/{@code fileId}
 * are identifiers (decimal strings); {@code partSize} and {@code version} are
 * numeric quantities and remain JSON numbers; {@code partCount}/
 * {@code uploadedParts} are 32-bit integers.
 */
public record UploadSessionResponse(
        UploadMode mode,
        Long uploadId,
        Long fileId,
        UploadSessionStatus status,
        @NumericLong Long partSize,
        Integer partCount,
        List<Integer> uploadedParts,
        Instant expiresAt,
        @NumericLong long version) {
}
