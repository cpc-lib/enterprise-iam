package com.enterprise.iam.authorization.interfaces.http.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateDataScopeRequest(
        @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,63}$") String scopeCode,
        @NotBlank @Size(max = 128) String scopeName,
        @NotNull String scopeType,
        Long customPolicyId) {
}
