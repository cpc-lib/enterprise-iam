package com.enterprise.iam.file.interfaces.rest.dto;

import com.enterprise.iam.file.domain.model.FileAccessMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FileAccessRequest(
        @NotNull Long resourceId,
        @NotBlank @Size(min = 1, max = 128) String resourceInstanceKey,
        Long referenceId,
        FileAccessMode requestedMode) {
}
