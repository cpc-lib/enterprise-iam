package com.enterprise.iam.organization.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTeamRequest(
        @NotNull Long organizationId,
        Long parentTeamId,
        @NotBlank @Pattern(regexp = "^[a-z][a-z0-9-]{2,63}$") String teamCode,
        @NotBlank @Size(max = 128) String teamName) {
}
