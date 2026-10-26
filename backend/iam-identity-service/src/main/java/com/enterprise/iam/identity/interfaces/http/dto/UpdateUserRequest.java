package com.enterprise.iam.identity.interfaces.http.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank @Size(max = 128) String displayName,
        @Size(max = 320) String email,
        @Size(max = 32) String phone,
        Long departmentId,
        Long positionId,
        @NotNull Long version
) {}
