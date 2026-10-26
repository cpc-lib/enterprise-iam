package com.enterprise.iam.organization.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record UpdatePositionRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{2,64}$") String positionCode,
        @NotBlank String positionName,
        Long organizationId,
        int sortOrder,
        String description,
        @NotNull Long version
) {}
