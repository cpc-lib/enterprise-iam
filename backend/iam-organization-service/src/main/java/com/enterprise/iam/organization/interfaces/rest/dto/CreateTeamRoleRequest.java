package com.enterprise.iam.organization.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTeamRoleRequest(
        @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,63}$") String roleCode,
        @NotBlank @Size(max = 128) String roleName,
        @Size(max = 500) String description) {
}
