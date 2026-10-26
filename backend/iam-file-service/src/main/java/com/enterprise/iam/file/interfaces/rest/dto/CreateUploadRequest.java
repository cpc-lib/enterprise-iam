package com.enterprise.iam.file.interfaces.rest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUploadRequest(
        @NotBlank @Size(min = 1, max = 255) String fileName,
        @NotNull @Min(1) @Max(53687091200L) Long fileSize,
        @NotNull @Pattern(regexp = "^[a-f0-9]{64}$") String sha256,
        @NotBlank @Size(min = 3, max = 255) String declaredContentType,
        @NotNull @Min(1) Long resourceId,
        @NotBlank @Size(min = 1, max = 128) String resourceInstanceKey,
        Long fieldId) {
}
