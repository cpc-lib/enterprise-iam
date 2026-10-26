package com.enterprise.iam.organization.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateOrganizationRequest(
        @NotBlank @Pattern(regexp = "^[a-z][a-z0-9-]{2,63}$") String orgCode,
        @NotBlank @Size(max = 128) String orgName,
        Long parentId) {
}
