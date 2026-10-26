package com.enterprise.iam.file.interfaces.rest.dto;

import com.enterprise.iam.file.domain.model.FileReferenceType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateFileReferenceRequest(
        @NotNull @Min(1) Long resourceId,
        @NotBlank @Size(min = 1, max = 128) String resourceInstanceKey,
        @NotNull FileReferenceType referenceType,
        Long fieldId,
        @Min(0) @Max(100000) Integer sortOrder) {
}
