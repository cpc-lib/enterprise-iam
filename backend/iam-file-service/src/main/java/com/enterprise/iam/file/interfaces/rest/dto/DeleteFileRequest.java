package com.enterprise.iam.file.interfaces.rest.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DeleteFileRequest(
        @NotNull @Min(0) Long version,
        @NotBlank @Size(min = 1, max = 500) String reason) {
}
