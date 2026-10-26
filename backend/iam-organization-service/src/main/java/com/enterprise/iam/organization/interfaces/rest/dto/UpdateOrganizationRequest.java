package com.enterprise.iam.organization.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record UpdateOrganizationRequest(
        @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,63}$") String orgCode,
        @NotBlank String orgName,
        Long parentId,
        @NotNull Long version
) {}
