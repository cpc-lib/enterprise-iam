package com.enterprise.iam.file.interfaces.rest.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReportPartRequest(
        @NotNull @Min(0) Long version,
        @NotBlank @Size(min = 1, max = 255) String storageEtag,
        @NotNull @Min(1) Long partSize,
        @Pattern(regexp = "^[a-f0-9]{64}$") String partSha256) {
}
