package com.enterprise.iam.file.interfaces.rest.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record SignPartRequest(
        @NotNull @Min(0) Long version,
        @NotNull @Min(1) Long expectedSize,
        @Pattern(regexp = "^[a-f0-9]{64}$") String partSha256) {
}
