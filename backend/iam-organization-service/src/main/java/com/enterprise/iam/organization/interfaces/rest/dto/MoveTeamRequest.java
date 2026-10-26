package com.enterprise.iam.organization.interfaces.rest.dto;

import jakarta.validation.constraints.NotNull;

public record MoveTeamRequest(
        Long newParentTeamId,
        @NotNull Long version) {
}
