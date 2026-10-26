package com.enterprise.iam.file.interfaces.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Max;

import java.util.List;

public record CompleteUploadRequest(
        @NotNull @Min(0) Long version,
        @NotNull @Pattern(regexp = "^[a-f0-9]{64}$") String wholeSha256,
        @NotEmpty @Size(min = 1, max = 10000) List<@Valid PartEtag> parts) {

    public record PartEtag(
            @NotNull @Min(1) @Max(10000) Integer partNumber,
            @NotBlank @Size(min = 1, max = 255) String storageEtag) {
    }
}
