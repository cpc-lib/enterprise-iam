package com.enterprise.iam.identity.interfaces.http.dto;

import com.enterprise.iam.identity.domain.model.IdentityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Size(max = 128) String displayName,
        @NotNull Identity identity,
        @Size(min = 15, max = 128) String initialPassword,
        Long departmentId,
        Long positionId,
        String email,
        String phone) {

    public CreateUserRequest(
            String displayName,
            Identity identity,
            String initialPassword) {
        this(displayName, identity, initialPassword, null, null, null, null);
    }

    public record Identity(
            @NotNull IdentityType type,
            @NotBlank @Size(max = 320) String value) {
    }
}
